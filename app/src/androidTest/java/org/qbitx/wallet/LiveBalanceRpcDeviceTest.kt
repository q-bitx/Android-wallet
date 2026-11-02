package org.qbitx.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.network.NodeRpcClient

/** Opt-in, read-only live check. No personal address or TXID is stored in the source. */
@RunWith(AndroidJUnit4::class)
class LiveBalanceRpcDeviceTest {
    @Test fun explicitlyRequestedMainnetReceiptIsPresentAndClassifiedCorrectly()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        val address=args.getString("live_address")
        val txid=args.getString("live_txid")
        val sat=args.getString("live_amount_sat")?.toLongOrNull()
        assumeTrue("Requires explicit public receipt parameters",address!=null && txid!=null && sat!=null)
        val rpc=NodeRpcClient()
        assertEquals("main",rpc.getBlockchainInfo().chain)
        val scan=rpc.scanTxOutSet(address!!)
        val received=scan.unspents.firstOrNull { it.txid==txid && it.vout==0 }
        assertNotNull("Specified confirmed receipt missing from the UTXO scan",received)
        assertEquals(sat!!,Math.round(received!!.amount*100_000_000))
        assertFalse(received.isCoinbase)
        assertTrue(received.confirmations>0)
        assertTrue(scan.totalAmount-scan.immatureAmount >= received.amount)
    }
}
