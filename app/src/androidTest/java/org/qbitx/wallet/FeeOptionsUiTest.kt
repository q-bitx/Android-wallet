package org.qbitx.wallet

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.crypto.*
import org.qbitx.wallet.ui.*
import org.qbitx.wallet.ui.theme.QBitXWalletTheme

@RunWith(AndroidJUnit4::class)
class FeeOptionsUiTest {
    @get:Rule val compose=createAndroidComposeRule<AuditPreviewActivity>()
    private val address=AddressUtils.hash160ToWitnessAddress(ByteArray(20) { (it+1).toByte() })
    @Test fun equalRatesShowOneChoiceAndTheConfirmationContainsTheActualRate() {
        var policy:String?=null
        compose.setContent { QBitXWalletTheme { SendScreen(
            WalletUiState(hasWallet=true,nodeConnected=true,balance=1.0,feeLow=5.0,feeNormal=5.0,feeHigh=5.0),
            onSend={_,_,p->policy=p},onBack={}) } }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(address)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("0.1")
        compose.onNodeWithTag("fee_choice_normal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("fee_choice_low").assertDoesNotExist()
        compose.onNodeWithTag("fee_choice_high").assertDoesNotExist()
        compose.onNodeWithText("Recommended").assertExists()
        compose.onNodeWithText("Send").performScrollTo().performClick()
        compose.onNodeWithText("Fees: 5 sat/vB").assertIsDisplayed()
        assertNull(policy)
        compose.onNodeWithText("Confirm").performClick()
        assertEquals("normal",policy)
    }
    @Test fun distinctRateSelectionIsPreservedInTheSendCallback() {
        var policy:String?=null
        compose.setContent { QBitXWalletTheme { SendScreen(
            WalletUiState(hasWallet=true,nodeConnected=true,balance=1.0,feeLow=1.0,feeNormal=5.1,feeHigh=15.0),
            onSend={_,_,p->policy=p},onBack={}) } }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(address)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("0.1")
        compose.onNodeWithTag("fee_choice_high").performScrollTo().performClick()
        compose.onNodeWithText("15 sat/vB").assertExists()
        compose.onNodeWithText("6 sat/vB").assertExists()
        compose.onNodeWithText("Send").performScrollTo().performClick()
        compose.onNodeWithText("Fees: 15 sat/vB").assertIsDisplayed()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals("high",policy)
    }
}
