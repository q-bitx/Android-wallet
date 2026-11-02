package org.qbitx.wallet

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.qbitx.wallet.crypto.AddressUtils
import org.qbitx.wallet.network.UtxoScanDecoder
import org.qbitx.wallet.ui.RecoverySafetyQuiz

class BalanceRecoveryTest {
    private val id=ByteArray(20) { (it+1).toByte() }
    private val legacy=AddressUtils.hash160ToAddress(id)
    private val native=AddressUtils.hash160ToWitnessAddress(id)
    private fun response(success: Boolean=true,total: String="3.00000001",duplicate: Boolean=false)=JsonParser.parseString("""
        {"success":$success,"height":200,"total_amount":$total,"unspents":[
        {"txid":"${"a".repeat(64)}","vout":0,"height":1,"amount":1.00000001,"scriptPubKey":"76a914${AddressUtils.toHex(id)}88c4"},
        {"txid":"${if(duplicate) "a".repeat(64) else "b".repeat(64)}","vout":0,"height":150,"amount":2.00000000,"scriptPubKey":"5214${AddressUtils.toHex(id)}"}]}
    """).asJsonObject
    @Test fun bothAddressTypesAndCoinbaseMaturityKeepExactBalance()= runBlocking {
        val result=UtxoScanDecoder.decode(response(),legacy,native) { _, _ -> true }
        assertEquals(3.00000001,result.totalAmount,0.0)
        assertEquals(2.0,result.immatureAmount,0.0)
        assertEquals(49,result.immatureBlocks)
        assertEquals(listOf(legacy,native),result.unspents.map { it.address })
        val spendable=result.totalAmount-result.immatureAmount
        assertEquals(1.00000001,spendable,0.000000001)
    }
    @Test fun failedIncompleteDuplicateAndInconsistentScansNeverBecomeZeroBalance()=runBlocking {
        suspend fun rejects(block: suspend ()->Unit) { try { block() } catch(e: Exception) { return }; fail("Expected rejection") }
        rejects { UtxoScanDecoder.decode(response(false),legacy,native) { _, _ -> false } }
        rejects { UtxoScanDecoder.decode(response(total="4"),legacy,native) { _, _ -> false } }
        rejects { UtxoScanDecoder.decode(response(duplicate=true),legacy,native) { _, _ -> false } }
        rejects { UtxoScanDecoder.decode(response(),legacy,null) { _, _ -> false } }
        rejects { UtxoScanDecoder.decode(response(),legacy,native) { _, _ -> error("RPC lookup failed") } }
    }
    @Test fun maturityBoundaryUsesOutputHeightAndOnlyChecksRecentOutputs()=runBlocking {
        val scan=response()
        val rows=scan.getAsJsonArray("unspents")
        rows[0].asJsonObject.addProperty("height",101) // exactly 100 confirmations
        rows[1].asJsonObject.addProperty("height",102) // 99 confirmations
        val checked=mutableListOf<Pair<String,Int>>()
        val result=UtxoScanDecoder.decode(scan,legacy,native) { txid,height -> checked+=txid to height;true }
        assertEquals(listOf("b".repeat(64) to 102),checked)
        assertEquals(2.0,result.immatureAmount,0.0);assertEquals(1,result.immatureBlocks)
    }
    @Test fun securityQuizRequiresCorrectFirstAnswerBeforeSecond() {
        try { RecoverySafetyQuiz().next(); fail("Skipped first question") } catch(e: IllegalStateException) { }
        try { RecoverySafetyQuiz().answer(false).next(); fail("Accepted wrong answer") } catch(e: IllegalStateException) { }
        val second=RecoverySafetyQuiz().answer(true).next()
        assertEquals(1,second.question); assertNull(second.correct)
        assertEquals(false,second.answer(false).correct)
        assertEquals(0,RecoverySafetyQuiz().question)
    }
}
