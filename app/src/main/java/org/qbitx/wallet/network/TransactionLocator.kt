package org.qbitx.wallet.network

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException

/** Locate confirmed history without txindex. Absence of a UTXO is never confirmation. */
internal class TransactionLocator(private val rpc: suspend (String,List<Any>) -> JsonObject) {
    private val blocks=BlockCoinbaseLookup(rpc)
    private suspend fun attempt(block:suspend ()->JsonObject):JsonObject? = try { block() }
        catch(e:CancellationException) { throw e } catch(_:Exception) { null }

    suspend fun find(txid:String, height:Int?=null, blockHash:String?=null):JsonObject? {
        require(txid.matches(Regex("[0-9a-f]{64}")))
        if(height!=null && height>=0) attempt { blocks.transaction(txid,height) }?.let { return it }
        if(blockHash!=null && blockHash.matches(Regex("[0-9a-f]{64}")))
            attempt { blocks.transactionAtHash(txid,blockHash) }?.let { return it }
        attempt {
            val tx=requireNotNull(rpc("getrawtransaction",listOf(txid,true)).getAsJsonObject("result"))
            require(tx.get("txid")?.asString==txid)
            val hash=tx.get("blockhash")?.asString
            if(hash!=null) blocks.transactionAtHash(txid,hash)
            else {
                require((tx.get("confirmations")?.asInt ?: 0)==0)
                tx
            }
        }?.let { return it }
        // Locally signed payments have recipient and optional change outputs.
        // This recovers old pending records even when no block metadata was saved.
        for(vout in 0..1) attempt {
            val output=requireNotNull(rpc("gettxout",listOf(txid,vout,false)).getAsJsonObject("result"))
            val confirmations=requireNotNull(output.get("confirmations")).asInt
            val tip=requireNotNull(output.get("bestblock")).asString
            require(confirmations>0 && tip.matches(Regex("[0-9a-f]{64}")))
            val header=requireNotNull(rpc("getblockheader",listOf(tip)).getAsJsonObject("result"))
            val tipHeight=requireNotNull(header.get("height")).asInt
            require(header.get("hash")?.asString==tip && tipHeight>=0 && confirmations<=tipHeight+1)
            blocks.transaction(txid,tipHeight-confirmations+1)
        }?.let { return it }
        return null
    }
}
