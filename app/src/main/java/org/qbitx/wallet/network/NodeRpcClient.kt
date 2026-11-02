package org.qbitx.wallet.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Stateless JSON-RPC client for the Q-BitX public proxy.
 * Only uses blockchain queries + tx building + broadcasting.
 * No wallet operations — keys never leave the device.
 */
class NodeRpcClient(
    private var rpcUrl: String = "https://rpc.qbitx.org/"
) {
    private val requestPacing = Mutex()
    private var nextRequestNanos = 0L
    private suspend fun paceRequest() = requestPacing.withLock {
        val waitMillis = ((nextRequestNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0)
        if (waitMillis > 0) delay(waitMillis + 1)
        nextRequestNanos = System.nanoTime() + 400_000_000L
    }
    companion object {
        fun normalizeRpcUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) {
                throw RpcException("RPC-URL darf nicht leer sein")
            }

            val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }

            val httpUrl = candidate.toHttpUrlOrNull()
                ?: throw RpcException("Ungültige RPC-URL. Bitte http:// oder https:// mit gültigem Host angeben.")

            if (!httpUrl.isHttps || httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty() || httpUrl.fragment != null) {
                throw RpcException("RPC requires HTTPS without embedded credentials or fragments")
            }
            if (httpUrl.host.isBlank()) {
                throw RpcException("Ungültige RPC-URL. Host fehlt.")
            }

            return httpUrl.toString().trimEnd('/')
        }
    }

    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    // Platform trust store and hostname verification are mandatory.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val requestId = java.util.concurrent.atomic.AtomicInteger(0)

    fun configure(rpcUrl: String) {
        this.rpcUrl = normalizeRpcUrl(rpcUrl)
    }

    private fun buildUrl(): String = normalizeRpcUrl(rpcUrl)

    /**
     * Translate a non-2xx HTTP response into a clear, user-facing RpcException.
     * Especially handles common proxy errors (413, 404, 405, 5xx) instead of
     * dumping raw HTML from nginx error pages.
     */
    private fun httpErrorMessage(httpCode: Int, body: String?): String = when (httpCode) {
        413 -> "Server-Limit überschritten (HTTP 413). Der RPC-Proxy lehnt zu große Requests ab — " +
                "PQ-Transaktionen sind durch die Dilithium-Signatur (~5 KB pro Input) zu groß für diesen Node. " +
                "Bitte einen Node mit höherem nginx client_max_body_size verwenden " +
                "(z. B. eigenen qbitx-Node oder anderen öffentlichen Endpoint)."
        404 -> "RPC-Endpunkt nicht gefunden (HTTP 404). RPC-URL prüfen."
        405 -> "Falsche RPC-URL (HTTP 405 Method Not Allowed). Der Server akzeptiert kein POST an diesem Pfad — " +
                "URL korrigieren (häufig fehlt der RPC-Pfad oder der Port)."
        401, 403 -> "Authentifizierung fehlgeschlagen (HTTP $httpCode). RPC-User/Passwort prüfen."
        in 500..599 -> "Node nicht erreichbar (HTTP $httpCode). Bitte später erneut versuchen."
        else -> {
            val snippet = body?.take(200)?.replace("\\s+".toRegex(), " ")?.trim().orEmpty()
            "Node-Fehler HTTP $httpCode${if (snippet.isNotEmpty()) ": $snippet" else ""}"
        }
    }

    /** Execute a JSON-RPC call and return the result as JsonObject. */
    suspend fun call(method: String, vararg params: Any?): JsonObject = withContext(Dispatchers.IO) {
        val id = requestId.incrementAndGet()
        val body = gson.toJson(mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "method" to method,
            "params" to params.toList()
        ))

        if (body.toByteArray(Charsets.UTF_8).size > 102_400) {
            throw RpcException("Transaction exceeds the public RPC request limit. Send a smaller amount.")
        }
        val requestBuilder = Request.Builder()
            .url(buildUrl())
            .post(body.toRequestBody(JSON))

        val request = requestBuilder.build()
        paceRequest()
        val (httpCode, responseBody) = client.newCall(request).execute().use { response ->
            val source = response.body?.source() ?: throw RpcException("Empty RPC response")
            if (source.request(8_000_001L)) throw RpcException("RPC response exceeds 8 MB limit")
            response.code to source.readUtf8()
        }

        // Give a clear error for common HTTP failures (proxy 413, wrong path 405, etc.)
        // before attempting JSON parse — otherwise users see raw nginx HTML.
        if (httpCode !in 200..299) {
            throw RpcException(httpErrorMessage(httpCode, responseBody))
        }

        if (responseBody == null) {
            throw RpcException("Leere Antwort vom Node")
        }

        val json = try {
            JsonParser.parseString(responseBody).asJsonObject
        } catch (e: Exception) {
            throw RpcException("Ungültige Antwort vom Node (kein JSON): ${responseBody.take(200)}")
        }

        if (json.has("error") && !json.get("error").isJsonNull) {
            val error = json.getAsJsonObject("error")
            val code = error.get("code")?.asInt ?: -1
            val message = error.get("message")?.asString ?: "Unknown RPC error"
            throw RpcException("RPC-Fehler $code: $message")
        }

        if (json.get("id")?.asInt != id || !json.has("result")) throw RpcException("Mismatched or incomplete RPC response")
        json
    }

    /**
     * Scan the UTXO set for a specific address.
     * Stateless — works for ANY address without a wallet.
     */
    suspend fun scanTxOutSet(address: String, extraAddress: String? = null): ScanResult {
        // One scan pass covers both of the wallet's addresses (legacy "M..." and,
        // if given, the native PQ witness "dil1q..." address of the same key).
        val scanObjects = com.google.gson.JsonArray().apply {
            add("addr($address)")
            if (!extraAddress.isNullOrEmpty()) add("addr($extraAddress)")
        }
        val result = call("scantxoutset", "start", scanObjects)
        val obj = result.getAsJsonObject("result")
            ?: throw RpcException("Invalid UTXO scan response")
        val maturityLookup = BlockCoinbaseLookup { method, params -> call(method,*params.toTypedArray()) }
        return UtxoScanDecoder.decode(obj,address,extraAddress,maturityLookup::isCoinbase)
    }

    /**
     * Create an unsigned raw transaction (stateless).
     * Uses the node to correctly encode addresses → scriptPubKeys.
     */
    suspend fun createRawTransaction(
        inputs: List<Utxo>,
        recipientAddress: String,
        recipientAmount: Double,
        changeAddress: String?,
        changeAmount: Double?
    ): String = withContext(Dispatchers.IO) {
        val id = requestId.incrementAndGet()

        val inputsArr = JsonArray()
        for (utxo in inputs) {
            inputsArr.add(JsonObject().apply {
                addProperty("txid", utxo.txid)
                addProperty("vout", utxo.vout)
            })
        }

        val outputsArr = JsonArray()
        outputsArr.add(JsonObject().apply {
            addProperty(recipientAddress, recipientAmount)
        })
        if (changeAddress != null && changeAmount != null && changeAmount > 0.0) {
            outputsArr.add(JsonObject().apply {
                addProperty(changeAddress, changeAmount)
            })
        }

        val paramsArr = JsonArray().apply {
            add(inputsArr)
            add(outputsArr)
        }

        val requestBody = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", id)
            addProperty("method", "createrawtransaction")
            add("params", paramsArr)
        }

        val body = requestBody.toString()

        val requestBuilder = Request.Builder()
            .url(buildUrl())
            .post(body.toRequestBody(JSON))

        val req = requestBuilder.build()
        val response = client.newCall(req).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            throw RpcException(httpErrorMessage(response.code, responseBody))
        }
        if (responseBody == null) {
            throw RpcException("Leere Antwort vom Node")
        }

        val json = JsonParser.parseString(responseBody).asJsonObject
        if (json.has("error") && !json.get("error").isJsonNull) {
            val error = json.getAsJsonObject("error")
            val code = error.get("code")?.asInt ?: -1
            val message = error.get("message")?.asString ?: "Unknown RPC error"
            throw RpcException("RPC-Fehler $code: $message")
        }

        json.get("result")?.asString ?: throw RpcException("createrawtransaction lieferte kein Ergebnis")
    }

    /** Broadcast a signed raw transaction. Returns the txid. */
    suspend fun sendRawTransaction(hexTx: String): String {
        val result = call("sendrawtransaction", hexTx)
        return result.get("result")?.asString ?: throw RpcException("Broadcast fehlgeschlagen")
    }

    /**
     * Estimate fee rate via the node's estimatesmartfee.
     * @param confTarget Number of blocks for confirmation target (e.g. 2, 6, 25)
     * @return fee rate in sat/vB, or null if estimation unavailable
     */
    suspend fun estimateSmartFee(confTarget: Int): Double? {
        return try {
            val result = call("estimatesmartfee", confTarget)
            val obj = result.getAsJsonObject("result") ?: return null
            val feeRateBtcKb = obj.get("feerate")?.asDouble ?: return null
            // Convert BTC/kB to sat/vB: BTC/kB * 1e8 / 1000
            feeRateBtcKb * 1e8 / 1000.0
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetch dynamic fee rates for low/normal/high.
     * Returns a Triple(low, normal, high) in sat/vB.
     * Falls back to defaults if the node doesn't support estimatesmartfee.
     */
    suspend fun fetchFeeRates(): Triple<Double, Double, Double> = coroutineScope {
        val highDef = async { estimateSmartFee(2) }   // next 2 blocks
        val normDef = async { estimateSmartFee(6) }   // next 6 blocks
        val lowDef  = async { estimateSmartFee(25) }  // next 25 blocks
        val high = highDef.await() ?: 15.0
        val norm = normDef.await() ?: 5.0
        val low  = lowDef.await()  ?: 1.0
        Triple(
            low.coerceAtLeast(1.0),
            norm.coerceAtLeast(1.0),
            high.coerceAtLeast(1.0)
        )
    }

    /** Validate an address via the node. Returns true if valid. */
    suspend fun validateAddress(address: String): Boolean {
        return try {
            val result = call("validateaddress", address)
            val obj = result.getAsJsonObject("result") ?: return false
            obj.get("isvalid")?.asBoolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** Get blockchain info (stateless). */
    suspend fun getBlockchainInfo(): BlockchainInfo {
        val result = call("getblockchaininfo")
        val obj = result.getAsJsonObject("result")
            ?: throw RpcException("Invalid getblockchaininfo response")
        return BlockchainInfo(
            chain = obj.get("chain")?.asString ?: "unknown",
            blocks = obj.get("blocks")?.asInt ?: 0,
            headers = obj.get("headers")?.asInt ?: 0,
            bestBlockHash = obj.get("bestblockhash")?.asString ?: ""
        )
    }

    /** Get full transaction details (verbose). */
    suspend fun getTransactionDetails(txid: String, blockHeight: Int? = null, blockHash: String? = null): TxDetail? {
        return try {
            val locator = TransactionLocator { method, params -> call(method,*params.toTypedArray()) }
            val tx = locator.find(txid,blockHeight,blockHash) ?: return null
            val confs = tx.get("confirmations")?.asInt ?: 0
            val time = tx.get("time")?.asLong ?: tx.get("blocktime")?.asLong ?: 0L

            val vouts = tx.getAsJsonArray("vout")?.map { v ->
                val vo = v.asJsonObject
                val value = vo.get("value")?.asDouble ?: 0.0
                val spk = vo.getAsJsonObject("scriptPubKey")
                val addrs = mutableListOf<String>()
                spk?.get("address")?.asString?.let { addrs.add(it) }
                if (addrs.isEmpty()) {
                    spk?.getAsJsonArray("addresses")?.forEach { a -> addrs.add(a.asString) }
                }
                TxVout(value, addrs)
            } ?: emptyList()

            val vinList = tx.getAsJsonArray("vin")?.mapNotNull { v ->
                val vi = v.asJsonObject
                if (vi.has("coinbase")) null
                else {
                    val inTxid = vi.get("txid")?.asString ?: return@mapNotNull null
                    val inVout = vi.get("vout")?.asInt ?: 0
                    TxVin(inTxid, inVout)
                }
            } ?: emptyList()

            TxDetail(txid, confs, time, vouts, vinList,tx.get("blockhash")?.asString)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Check if a specific address funded any input of a transaction.
     * Looks up the previous TX for each vin and checks the specific output.
     */
    suspend fun isAddressSpender(detail: TxDetail, address: String): Boolean {
        for (vin in detail.vinList) {
            try {
                val prevTx = getTransactionDetails(vin.txid) ?: continue
                if (vin.vout < prevTx.voutList.size) {
                    if (prevTx.voutList[vin.vout].addresses.contains(address)) {
                        return true
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (_: Exception) {}
        }
        return false
    }

    /** History cursor remains unchanged while the public RPC lacks a compatible history index. */
    suspend fun discoverAllTxIds(
        address: String,
        blockHeight: Int = 0,
        lastScannedHeight: Int = 0,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Pair<List<String>, Int> {
        // The public node exposes Core scanblocks, not the upstream wallet's custom
        // numeric scanblocks API. Its basic filter index is disabled. Do not claim
        // a successful scan or advance the cursor; UTXOs and local history remain available.
        return Pair(emptyList(), lastScannedHeight)
    }

    /** Fetch QBX/USDT price from KlingEx public API. Returns null on failure. */
    suspend fun fetchQbxPriceUsdt(): Double? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.klingex.io/api/tickers")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val arr = JsonParser.parseString(body).asJsonArray
            for (elem in arr) {
                val obj = elem.asJsonObject
                if (obj.get("ticker_id")?.asString == "QBX_USDT") {
                    return@withContext obj.get("last_price")?.asString?.toDoubleOrNull()
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Test connection to the proxy/node. */
    suspend fun testConnection(): Boolean {
        return try {
            getBlockchainInfo()
            true
        } catch (_: Exception) {
            false
        }
    }
}

data class SendResult(val txid: String, val fee: Long)
data class BlockchainInfo(val chain: String, val blocks: Int, val headers: Int, val bestBlockHash: String)
data class Utxo(val txid: String, val vout: Int, val address: String, val amount: Double, val confirmations: Int, val scriptPubKey: String, val isCoinbase: Boolean = false, val height: Int = -1)
data class ScanResult(val totalAmount: Double, val unspents: List<Utxo>, val immatureAmount: Double = 0.0, val immatureBlocks: Int = 0)

data class TxVout(val value: Double, val addresses: List<String>)
data class TxVin(val txid: String, val vout: Int)
data class TxDetail(
    val txid: String,
    val confirmations: Int,
    val time: Long,       // unix epoch seconds
    val voutList: List<TxVout>,
    val vinList: List<TxVin>, // txid:vout pairs of inputs
    val blockHash: String? = null
)

class RpcException(message: String) : Exception(message)
