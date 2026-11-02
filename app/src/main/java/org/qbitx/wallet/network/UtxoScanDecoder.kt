package org.qbitx.wallet.network

import com.google.gson.JsonObject
import org.qbitx.wallet.crypto.AddressUtils
import org.qbitx.wallet.crypto.TransactionPolicy

/** Parse the same combined M/dil1q UTXO scan as upstream, with exact satoshi accounting. */
internal object UtxoScanDecoder {
    suspend fun decode(obj: JsonObject,address: String,extraAddress: String?,coinbase: suspend (String,Int)->Boolean): ScanResult {
        require(obj.get("success")?.asBoolean==true) { "UTXO scan did not complete" }
        val height=requireNotNull(obj.get("height")).asInt
        require(height>=0)
        val own=setOfNotNull(address,extraAddress?.takeIf { it.isNotBlank() })
        val outputs=requireNotNull(obj.getAsJsonArray("unspents")) { "Missing UTXOs" }
        val seen=mutableSetOf<String>()
        val coinbases=mutableMapOf<Pair<String,Int>,Boolean>()
        var totalSat=0L
        var immatureSat=0L
        val unspents=outputs.map { elem ->
            val u=elem.asJsonObject
            val txid=u.get("txid").asString
            val vout=u.get("vout").asInt
            val outputHeight=u.get("height").asInt
            require(txid.matches(Regex("[0-9a-f]{64}")) && vout>=0 && outputHeight in 0..height && seen.add("$txid:$vout")) { "Invalid or duplicate UTXO" }
            val sat=u.get("amount").asBigDecimal.movePointRight(8).longValueExact()
            require(sat>0)
            totalSat=Math.addExact(totalSat,sat)
            val script=u.get("scriptPubKey").asString
            val owner=TransactionPolicy.outputAddress(AddressUtils.fromHex(script))
            require(owner in own) { "RPC returned an output outside this wallet" }
            val confs=height-outputHeight+1
            val isCoinbase=if(confs>=100) false else coinbases[txid to outputHeight] ?: coinbase(txid,outputHeight).also { coinbases[txid to outputHeight]=it }
            if(isCoinbase && confs<100) immatureSat=Math.addExact(immatureSat,sat)
            Utxo(txid,vout,owner,sat/100_000_000.0,confs,script,isCoinbase)
        }
        require(obj.get("total_amount").asBigDecimal.movePointRight(8).longValueExact()==totalSat) { "RPC balance and UTXO sum disagree" }
        val immature=unspents.filter { it.isCoinbase && it.confirmations<100 }
        return ScanResult(totalSat/100_000_000.0,unspents,immatureSat/100_000_000.0,
            if(immature.isEmpty()) 0 else 100-immature.maxOf { it.confirmations })
    }
}
