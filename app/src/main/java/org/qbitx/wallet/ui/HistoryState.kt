package org.qbitx.wallet.ui

import org.qbitx.wallet.data.TxRecord
import org.qbitx.wallet.network.TxDetail

internal object HistoryState {
    fun reconcile(local:TxRecord, detail:TxDetail?):TxRecord = local.copy(
        confirmations=detail?.confirmations ?: -1,
        blockHash=detail?.blockHash ?: local.blockHash)

    fun pendingOutgoing(history:List<TxRecord>):Double = history.filter { it.direction=="out" && it.confirmations==0 }
        .sumOf { tx ->
            val fee=tx.fee.trim().split(' ').firstOrNull()?.replace(',','.')?.toDoubleOrNull() ?: 0.0
            -(tx.amount+fee)
        }

    fun canReleasePendingLocks(history:List<TxRecord>):Boolean = history.none { it.direction=="out" && it.confirmations<=0 }
}
