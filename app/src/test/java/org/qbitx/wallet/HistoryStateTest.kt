package org.qbitx.wallet

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.qbitx.wallet.data.TxRecord
import org.qbitx.wallet.network.TxDetail
import org.qbitx.wallet.ui.HistoryState

class HistoryStateTest {
    private val pending=TxRecord("a".repeat(64),"recipient",0.005,"0,00002245 QBX",1,0,"out",0)
    @Test fun confirmedSendClearsPendingDeltaAndPersistsItsBlockHash() {
        assertEquals(-0.00502245,HistoryState.pendingOutgoing(listOf(pending)),1e-12)
        val detail=TxDetail(pending.txid,40,1,emptyList(),emptyList(),"b".repeat(64))
        val updated=HistoryState.reconcile(pending,detail)
        assertEquals(40,updated.confirmations);assertEquals(detail.blockHash,updated.blockHash)
        assertEquals(0.0,HistoryState.pendingOutgoing(listOf(updated)),0.0)
        assertTrue(HistoryState.canReleasePendingLocks(listOf(updated)))
        assertEquals(pending.amount,updated.amount,0.0);assertEquals(pending.fee,updated.fee)
    }
    @Test fun missingStatusRemainsUnknownAndDoesNotUnlockSpentInputs() {
        val unknown=HistoryState.reconcile(pending,null)
        assertEquals(-1,unknown.confirmations)
        assertFalse(HistoryState.canReleasePendingLocks(listOf(unknown)))
        assertEquals(0.0,HistoryState.pendingOutgoing(listOf(unknown)),0.0)
    }
    @Test fun oldStoredHistoryStillLoadsWithoutBlockMetadata() {
        val json=Gson().toJsonTree(pending).asJsonObject.apply { remove("blockHash") }.toString()
        val old=Gson().fromJson(json,TxRecord::class.java)
        assertEquals(pending,old);assertNull(old.blockHash)
    }
}
