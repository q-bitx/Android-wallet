package org.qbitx.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.network.*
import org.qbitx.wallet.crypto.*

@RunWith(AndroidJUnit4::class)
class NoTxIndexBalanceDeviceTest {
    @Test fun actualCoreTransferAndCoinbaseClassifyCorrectlyWithoutTxindex()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().context
        val fixture=JsonParser.parseString(context.assets.open("core-no-txindex.json").bufferedReader().use { it.readText() }).asJsonObject
        assertFalse(fixture["txindex"].asBoolean)
        val normal=fixture.getAsJsonObject("normal");val cb=fixture.getAsJsonObject("coinbase")
        val calls=mutableListOf<String>()
        val lookup=BlockCoinbaseLookup { method,params ->
            calls+=method
            JsonObject().apply {
                if(method=="getblockhash") {
                    assertEquals(normal["height"].asInt,params[0]);add("result",normal["blockhash"])
                } else {
                    assertEquals("getrawtransaction",method);assertEquals(3,params.size)
                    assertEquals(normal["blockhash"].asString,params[2])
                    add("result",(if(params[0]==normal["txid"].asString) normal else cb).getAsJsonObject("transaction"))
                }
            }
        }
        val scan=normal.getAsJsonObject("scan")
        val script=scan.getAsJsonArray("unspents")[0].asJsonObject["scriptPubKey"].asString
        val address=TransactionPolicy.outputAddress(AddressUtils.fromHex(script))!!
        val result=UtxoScanDecoder.decode(scan,address,null,lookup::isCoinbase)
        assertEquals(1.123,result.totalAmount-result.immatureAmount,0.0)
        assertFalse(result.unspents.single().isCoinbase)
        assertTrue(lookup.isCoinbase(cb["txid"].asString,cb["height"].asInt))
        assertEquals(1,calls.count { it=="getblockhash" })
    }
}
