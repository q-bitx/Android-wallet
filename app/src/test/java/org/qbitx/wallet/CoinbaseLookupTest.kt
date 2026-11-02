package org.qbitx.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.qbitx.wallet.network.*
import org.qbitx.wallet.crypto.*

class CoinbaseLookupTest {
    private val txid="a".repeat(64)
    private val hash="b".repeat(64)
    private fun result(value: String)=JsonParser.parseString("{\"result\":$value}").asJsonObject
    private fun transaction(coinbase:Boolean=false,active:Boolean=true):JsonObject = JsonObject().apply {
        addProperty("txid",txid);addProperty("blockhash",hash);addProperty("in_active_chain",active);addProperty("confirmations",1)
        add("vin",JsonParser.parseString(if(coinbase) "[{\"coinbase\":\"0102\"}]" else "[{\"txid\":\"${"c".repeat(64)}\",\"vout\":0}]") )
    }
    private fun wrap(tx:JsonObject)=JsonObject().apply { add("result",tx) }
    private suspend fun rejects(block:suspend ()->Unit) { try { block() } catch(e:Exception) { return };fail("Expected rejection") }

    @Test fun usesExplicitBlockHashAndNeverNeedsTheTransactionIndex()=runBlocking {
        val methods=mutableListOf<String>()
        val lookup=BlockCoinbaseLookup { method, params ->
            methods+=method
            when(method) {
                "getblockhash" -> { assertEquals(listOf(200),params);result("\"$hash\"") }
                "getrawtransaction" -> { assertEquals(listOf(txid,true,hash),params);wrap(transaction()) }
                else -> error("Unexpected RPC")
            }
        }
        assertFalse(lookup.isCoinbase(txid,200))
        assertFalse(lookup.isCoinbase(txid,200))
        assertEquals(listOf("getblockhash","getrawtransaction","getrawtransaction"),methods)
    }
    @Test fun immatureCoinbaseIsStillRecognized()=runBlocking {
        val lookup=BlockCoinbaseLookup { method,_ -> if(method=="getblockhash") result("\"$hash\"") else wrap(transaction(true)) }
        assertTrue(lookup.isCoinbase(txid,200))
    }
    @Test fun mismatchedMissingAndReorganizedTransactionsAreRejected()=runBlocking {
        val bad=listOf(transaction().apply { addProperty("txid","d".repeat(64)) },
            transaction().apply { addProperty("blockhash","d".repeat(64)) },transaction(active=false),
            transaction().apply { remove("in_active_chain") },transaction().apply { addProperty("confirmations",0) },
            transaction().apply { add("vin",JsonParser.parseString("[]")) },
            transaction().apply { add("vin",JsonParser.parseString("[{}]")) })
        for(item in bad) {
            val lookup=BlockCoinbaseLookup { method,_ -> if(method=="getblockhash") result("\"$hash\"") else wrap(item) }
            rejects { lookup.isCoinbase(txid,200) }
        }
        rejects { BlockCoinbaseLookup { _,_-> error("RPC unavailable") }.isCoinbase(txid,200) }
    }
    @Test fun realCoreTransferOf1123WorksWithTxindexDisabled()=runBlocking {
        val text=javaClass.getResource("/core-no-txindex.json")!!.readText()
        val fixture=JsonParser.parseString(text).asJsonObject
        assertFalse(fixture["txindex"].asBoolean)
        assertTrue(fixture["plain_lookup_error"].asString.contains("No such mempool transaction"))
        val normal=fixture.getAsJsonObject("normal")
        val coinbase=fixture.getAsJsonObject("coinbase")
        val lookup=BlockCoinbaseLookup { method,params ->
            when(method) {
                "getblockhash" -> { assertEquals(normal["height"].asInt,params[0]); result(normal["blockhash"].toString()) }
                "getrawtransaction" -> {
                    assertEquals(3,params.size);assertEquals(normal["blockhash"].asString,params[2])
                    wrap((if(params[0]==normal["txid"].asString) normal else coinbase).getAsJsonObject("transaction"))
                }
                else -> error("Unexpected RPC")
            }
        }
        val scan=normal.getAsJsonObject("scan")
        val script=scan.getAsJsonArray("unspents")[0].asJsonObject["scriptPubKey"].asString
        val address=TransactionPolicy.outputAddress(AddressUtils.fromHex(script))!!
        val balance=UtxoScanDecoder.decode(scan,address,null,lookup::isCoinbase)
        assertEquals(1.123,balance.totalAmount,0.0);assertEquals(0.0,balance.immatureAmount,0.0)
        assertFalse(balance.unspents.single().isCoinbase)
        assertTrue(lookup.isCoinbase(coinbase["txid"].asString,coinbase["height"].asInt))
    }
}
