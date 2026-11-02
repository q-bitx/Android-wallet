package org.qbitx.wallet

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.data.KeyManager
import org.qbitx.wallet.network.NodeRpcClient
import org.qbitx.wallet.ui.*
import org.qbitx.wallet.ui.theme.QBitXWalletTheme

/** Explicit public parameters only, empty emulator storage, no broadcasting. */
@RunWith(AndroidJUnit4::class)
class LiveHistoryStatusDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<AuditPreviewActivity>()
    @Test fun oldPendingRecordRefreshesInViewModelAndPersists()=runBlocking {
        val txid=InstrumentationRegistry.getArguments().getString("live_history_txid")
        assumeTrue("Explicit public transaction parameter required",txid!=null)
        val rpc=NodeRpcClient()
        val detail=rpc.getTransactionDetails(txid!!)!!
        assertTrue(detail.confirmations>0);assertNotNull(detail.blockHash)
        val manager=KeyManager(compose.activity)
        assumeFalse("Empty disposable emulator data required",manager.hasWallet())
        manager.createWallet("History audit");manager.confirmSeed()
        val recipient=detail.voutList.first().addresses.first()
        manager.addTxRecord(txid,recipient,detail.voutList.first().value,"0.00002245 QBX")
        lateinit var vm:WalletViewModel
        compose.runOnIdle {
            vm=ViewModelProvider(compose.activity,ViewModelProvider.AndroidViewModelFactory.getInstance(compose.activity.application))[WalletViewModel::class.java]
        }
        compose.setContent {
            val state by vm.uiState.collectAsState()
            QBitXWalletTheme { HistoryScreen(state,onBack={}) }
        }
        compose.waitUntil(90_000) { vm.uiState.value.txHistory.any { it.txid==txid && it.confirmations>0 } }
        compose.runOnIdle {
            val state=vm.uiState.value
            assertEquals(0.0,state.unconfirmedBalance,0.0)
            assertTrue(state.txHistory.single { it.txid==txid }.confirmations>0)
        }
        compose.onNodeWithText("Pending").assertDoesNotExist()
        val stored=KeyManager(compose.activity).getTxHistoryForActiveWallet().single { it.txid==txid }
        assertTrue(stored.confirmations>0);assertEquals(detail.blockHash,stored.blockHash)
        // Verify a saved block remains sufficient after every output is later spent.
        assertTrue(rpc.getTransactionDetails(txid,blockHash=stored.blockHash)!!.confirmations>0)
    }
    @Test fun savedConfirmationSurvivesProcessRestart()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("Explicit restart check required",args.getString("live_history_restart")=="true")
        val txid=args.getString("live_history_txid")!!
        val manager=KeyManager(compose.activity)
        val stored=manager.getTxHistoryForActiveWallet().single { it.txid==txid }
        assertTrue(stored.confirmations>0);assertNotNull(stored.blockHash)
        assertTrue(NodeRpcClient().getTransactionDetails(txid,blockHash=stored.blockHash)!!.confirmations>0)
    }
}
