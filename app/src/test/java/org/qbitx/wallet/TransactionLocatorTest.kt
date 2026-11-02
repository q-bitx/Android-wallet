package org.qbitx.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.qbitx.wallet.network.TransactionLocator

class TransactionLocatorTest {
    private val fixture=JsonParser.parseString(javaClass.getResource("/core-no-txindex.json")!!.readText()).asJsonObject
    private val n=fixture.getAsJsonObject("normal")
    private val id=n["txid"].asString
    private val hash=n["blockhash"].asString
    private fun wrap(value:com.google.gson.JsonElement?)=JsonObject().apply { add("result",value) }
    private fun output()=JsonParser.parseString("""{"bestblock":"$hash","confirmations":1}""").asJsonObject
    private fun header()=JsonParser.parseString("""{"hash":"$hash","height":${n["height"].asInt}}""").asJsonObject
    @Test fun knownUtxoHeightAndSavedBlockHashAvoidTxindex()=runBlocking {
        val calls=mutableListOf<String>()
        val locator=TransactionLocator { method,params ->
            calls+=method
            when(method) {
                "getblockhash"->wrap(n["blockhash"])
                "getrawtransaction"->{ assertEquals(listOf(id,true,hash),params);wrap(n["transaction"]) }
                else->error("Unexpected RPC")
            }
        }
        assertNotNull(locator.find(id,n["height"].asInt))
        assertEquals(listOf("getblockhash","getrawtransaction"),calls)
        calls.clear();assertNotNull(locator.find(id,blockHash=hash))
        assertEquals(listOf("getrawtransaction"),calls)
    }
    @Test fun oldPendingRecordUsesRecipientOrChangeToFindItsConfirmedBlock()=runBlocking {
        for(unspent in 0..1) {
            val locator=TransactionLocator { method,params -> when(method) {
                "getrawtransaction"->if(params.size==2) error("Index still building") else wrap(n["transaction"])
                "gettxout"->wrap(if(params[1]==unspent) output() else null)
                "getblockheader"->wrap(header())
                "getblockhash"->wrap(n["blockhash"])
                else->error("Unexpected RPC")
            } }
            assertEquals(1,locator.find(id)!!["confirmations"].asInt)
        }
    }
    @Test fun absenceOfEveryOutputNeverBecomesAConfirmation()=runBlocking {
        val locator=TransactionLocator { method,_ -> if(method=="gettxout") wrap(null) else error("Index unavailable") }
        assertNull(locator.find(id))
    }
    @Test fun actualMempoolResponseIsPendingAndCancellationIsNotSwallowed()=runBlocking {
        val pending=n.getAsJsonObject("transaction").deepCopy().apply { remove("blockhash");remove("confirmations");remove("in_active_chain") }
        val locator=TransactionLocator { method,_ -> assertEquals("getrawtransaction",method);wrap(pending) }
        assertNotNull(locator.find(id));assertFalse(locator.find(id)!!.has("blockhash"))
        try { TransactionLocator { _,_->throw CancellationException("cancelled") }.find(id);fail("Cancellation swallowed") }
        catch(_:CancellationException) { }
    }
    @Test fun orphanedSavedBlockDoesNotKeepAFalseConfirmedStatus()=runBlocking {
        val orphan=n.getAsJsonObject("transaction").deepCopy().apply { addProperty("in_active_chain",false) }
        val locator=TransactionLocator { method,params -> when(method) {
            "getrawtransaction"->if(params.size==3) wrap(orphan) else error("No mempool transaction")
            "gettxout"->wrap(null)
            else->error("Unexpected RPC")
        } }
        assertNull(locator.find(id,blockHash=hash))
    }
}
