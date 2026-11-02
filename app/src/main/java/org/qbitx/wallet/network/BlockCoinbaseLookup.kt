package org.qbitx.wallet.network

import com.google.gson.JsonObject

/** Per-scan lookup: an explicit block hash makes getrawtransaction independent of txindex. */
internal class BlockCoinbaseLookup(private val rpc: suspend (String,List<Any>) -> JsonObject) {
    private val blockHashes = mutableMapOf<Int,String>()
    suspend fun transaction(txid: String, outputHeight: Int): JsonObject {
        require(txid.matches(Regex("[0-9a-f]{64}")) && outputHeight >= 0)
        val hash = blockHashes[outputHeight] ?: rpc("getblockhash",listOf(outputHeight))
            .get("result")?.asString?.also {
                require(it.matches(Regex("[0-9a-f]{64}"))) { "Invalid block hash" }
                blockHashes[outputHeight] = it
            } ?: error("Missing block hash")
        return transactionAtHash(txid,hash)
    }

    suspend fun transactionAtHash(txid: String, hash: String): JsonObject {
        require(txid.matches(Regex("[0-9a-f]{64}")) && hash.matches(Regex("[0-9a-f]{64}")))
        val tx = rpc("getrawtransaction",listOf(txid,true,hash)).getAsJsonObject("result")
            ?: error("Missing transaction in its confirmed block")
        require(tx.get("txid")?.asString == txid && tx.get("blockhash")?.asString == hash) {
            "Transaction identity or block mismatch"
        }
        require(tx.get("in_active_chain")?.asBoolean == true && (tx.get("confirmations")?.asInt ?: 0) > 0) {
            "Transaction block is no longer in the active chain; refresh again"
        }
        return tx
    }

    suspend fun isCoinbase(txid: String, outputHeight: Int): Boolean {
        val tx = transaction(txid,outputHeight)
        val inputs = requireNotNull(tx.getAsJsonArray("vin")) { "Missing transaction inputs" }
        require(inputs.size() > 0) { "Empty transaction inputs" }
        if (inputs[0].asJsonObject.has("coinbase")) {
            require(inputs.size() == 1 && inputs[0].asJsonObject.get("coinbase").asString.matches(Regex("(?:[0-9a-fA-F]{2})+"))) { "Invalid coinbase input" }
            return true
        }
        require(inputs.all { input ->
            val vin=input.asJsonObject
            !vin.has("coinbase") && vin.get("txid")?.asString?.matches(Regex("[0-9a-f]{64}")) == true &&
                (vin.get("vout")?.asInt ?: -1) >= 0
        }) { "Invalid ordinary transaction inputs" }
        return false
    }
}
