package org.qbitx.wallet

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.data.TxRecord
import org.qbitx.wallet.ui.*
import org.qbitx.wallet.ui.theme.QBitXWalletTheme

@RunWith(AndroidJUnit4::class)
class HistoryStatusUiTest {
    @get:Rule val compose=createAndroidComposeRule<AuditPreviewActivity>()
    private fun show(confirmations:Int) {
        val record=TxRecord("a".repeat(64),"recipient",0.005,"0.00002245 QBX",1,0,"out",confirmations)
        compose.setContent { QBitXWalletTheme { HistoryScreen(WalletUiState(txHistory=listOf(record)),onBack={}) } }
    }
    @Test fun confirmedTransferShowsConfirmationCount() {
        show(40);compose.onNodeWithText("40 conf.").assertIsDisplayed()
        compose.onNodeWithText("Pending").assertDoesNotExist()
    }
    @Test fun verifiedPendingTransferShowsPending() {
        show(0);compose.onNodeWithText("Pending").assertIsDisplayed()
    }
    @Test fun unavailableStatusIsNotPresentedAsPending() {
        show(-1);compose.onNodeWithText("Status unavailable").assertIsDisplayed()
        compose.onNodeWithText("Pending").assertDoesNotExist()
    }
}
