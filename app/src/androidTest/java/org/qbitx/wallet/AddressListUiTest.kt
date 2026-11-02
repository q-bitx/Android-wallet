package org.qbitx.wallet

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.qbitx.wallet.ui.*
import org.qbitx.wallet.data.WalletInfo
import org.qbitx.wallet.ui.theme.QBitXWalletTheme

@RunWith(AndroidJUnit4::class)
class AddressListUiTest {
    @get:Rule val compose=createAndroidComposeRule<AuditPreviewActivity>()
    @Test fun selectedWalletAllWalletsBalancesAndReceiveUseTheCorrectAddress() {
        var received=""
        val wallets=listOf(WalletInfo(1,"First","M-first","dil1-first",true), WalletInfo(2,"Second","M-second","dil1-second",false))
        compose.setContent { QBitXWalletTheme { AddressListScreen(
            WalletUiState(address="M-first",wallets=wallets,addressBalances=mapOf("dil1-first" to 1.0,"M-first" to 2.0,"dil1-second" to 3.0)),
            onRefresh={},onReceive={received=it},onBack={}) } }
        compose.onNodeWithText("dil1-second").assertDoesNotExist()
        compose.onNodeWithText("M-first").assertIsDisplayed()

        compose.onAllNodesWithText("First")[0].performClick()
        compose.onNodeWithText("All wallets").performClick()
        compose.onNodeWithText("dil1-second").assertIsDisplayed()
        val top=compose.onNodeWithText("dil1-second").fetchSemanticsNode().boundsInRoot.top
        val lower=compose.onNodeWithText("M-first").fetchSemanticsNode().boundsInRoot.top
        assertTrue(top<lower)
        compose.onNodeWithText("dil1-second").performClick()
        compose.runOnIdle { assertEquals("dil1-second",received) }
    }
    @Test fun unknownBalanceIsNotDisplayedAsZero() {
        compose.setContent { QBitXWalletTheme { AddressListScreen(
            WalletUiState(address="M-first",wallets=listOf(WalletInfo(1,"First","M-first","dil1-first",false))),
            onRefresh={},onReceive={},onBack={}) } }
        compose.onNodeWithText("— QBX").assertIsDisplayed()
        compose.onNodeWithText("0.00000000 QBX").assertDoesNotExist()
    }
    @Test fun managementButtonsRemainSeparateAndClickable() {
        var renamed=false
        compose.setContent { QBitXWalletTheme { SettingsScreen(
            state=WalletUiState(hasWallet=true,address="M-first",activeWalletName="First",
                wallets=listOf(WalletInfo(1,"First","M-first"),WalletInfo(2,"Second","M-second"))),
            onConnect={},onExportBackup={""},onDeleteWallet={},onBack={},onSetPin={},onRemovePin={},
            onRenameWallet={renamed=true},onDeleteActiveWallet={},onSwitchWallet={},onAddWallet={}) } }
        compose.onNodeWithText("Rename").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Rename Wallet").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Remove").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Create new wallet").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("New Wallet").assertIsDisplayed()
        assertFalse(renamed)
    }
}

