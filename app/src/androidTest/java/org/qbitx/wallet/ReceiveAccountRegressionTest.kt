package org.qbitx.wallet

import android.content.ClipboardManager
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.ui.*
import org.qbitx.wallet.ui.theme.QBitXWalletTheme

/** Disposable emulator data only. Generates unfunded keys; never broadcasts. */
@RunWith(AndroidJUnit4::class)
class ReceiveAccountRegressionTest {
    @get:Rule val compose=createAndroidComposeRule<AuditPreviewActivity>()

    private fun assertQrAndClipboard(expected: String) {
        compose.onNodeWithTag("receive_address").assertTextEquals(expected)
        val bitmap=compose.onNodeWithTag("receive_qr").captureToImage().asAndroidBitmap()
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val decoded=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels))))
        assertEquals(expected,decoded.text)
        compose.onNodeWithText("Copy address").performClick()
        compose.runOnIdle {
            val clipboard=compose.activity.getSystemService(ClipboardManager::class.java)
            assertEquals(expected,clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test fun homeAccountSwitchUpdatesActualViewModelQrAndClipboardAcrossThreeAccounts() {
        lateinit var vm:WalletViewModel
        compose.runOnIdle {
            vm=ViewModelProvider(compose.activity,ViewModelProvider.AndroidViewModelFactory.getInstance(compose.activity.application))[WalletViewModel::class.java]
        }
        org.junit.Assume.assumeFalse("Requires empty disposable emulator app data", vm.uiState.value.hasWallet)
        compose.setContent {
            val state by vm.uiState.collectAsState()
            var receive by remember { mutableStateOf(false) }
            QBitXWalletTheme {
                if(receive) WalletReceiveScreen(state,onBack={receive=false})
                else WalletScreen(state,onCreateWallet={},onImportWallet={},onNavigateToSend={},
                    onNavigateToReceive={receive=true},onNavigateToSettings={},onNavigateToHistory={},
                    onRefresh={},onSwitchWallet={vm.switchWallet(it)},onAddWallet={})
            }
        }
        compose.runOnIdle { vm.createWallet("Regression") }
        compose.waitUntil(90_000) { vm.uiState.value.wallets.size==2 && !vm.uiState.value.isLoading }
        compose.runOnIdle { vm.confirmSeed();vm.createWallet("Third") }
        compose.waitUntil(90_000) { vm.uiState.value.wallets.size==3 && !vm.uiState.value.isLoading }
        val wallets=vm.uiState.value.wallets
        assertEquals(3,wallets.map { it.witnessAddress }.toSet().size)
        for(wallet in wallets+wallets.first()) {
            compose.onNodeWithTag("wallet_selector").performScrollTo().performClick()
            compose.onNodeWithTag("wallet_option_${wallet.id}").performClick()
            compose.waitUntil { vm.uiState.value.address==wallet.address }
            compose.onNodeWithTag("receive_action").performScrollTo().performClick()
            compose.onNodeWithTag("receive_wallet_name").assertTextEquals(wallet.name)
            val number=walletReceiveAddresses(wallets).indexOf(wallet.witnessAddress)+1
            compose.onNodeWithText("Address $number · PQ witness").assertExists()
            assertQrAndClipboard(wallet.witnessAddress)
            compose.onNodeWithContentDescription("Back").performClick()
        }
    }
    @Test fun addressListTargetKeepsItsOwnerEvenWhenAnotherAccountIsActive() {
        val manager=org.qbitx.wallet.data.KeyManager(compose.activity,auditPreferences())
        manager.createWallet("List target")
        val wallets=manager.listWallets()
        val active=wallets.first()
        val target=wallets.last()
        compose.setContent { QBitXWalletTheme {
            WalletReceiveScreen(WalletUiState(address=active.address,witnessAddress=active.witnessAddress,
                activeWalletName=active.name,wallets=wallets),onBack={},requestedAddress=target.witnessAddress)
        } }
        compose.onNodeWithTag("receive_wallet_name").assertTextEquals(target.name)
        compose.onNodeWithText("Address 2 · PQ witness").assertExists()
        assertQrAndClipboard(target.witnessAddress)
        compose.onNodeWithText(active.witnessAddress).assertDoesNotExist()
    }
}

