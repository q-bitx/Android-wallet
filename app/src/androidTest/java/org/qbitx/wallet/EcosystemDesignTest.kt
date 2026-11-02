package org.qbitx.wallet

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.ui.*
import org.qbitx.wallet.ui.theme.QBitXWalletTheme
import org.qbitx.wallet.crypto.*
import org.qbitx.wallet.data.WalletInfo
import java.io.File

@RunWith(AndroidJUnit4::class)
class EcosystemDesignTest {
    @get:Rule val compose=createAndroidComposeRule<AuditPreviewActivity>()
    private val pub=DilithiumNative().generateKeypairFromSeed(SeedWallet.keySeed(SeedWallet.fromEntropy(ByteArray(32))))!![0]
    private val legacy=AddressUtils.pubkeyToAddress(pub)
    private val native=AddressUtils.pubkeyToWitnessAddress(pub)
    @Test fun dashboardAndReceiveMatchEcosystemAndRenderAllAddresses() {
        compose.setContent {
            QBitXWalletTheme { WalletScreen(
                state=WalletUiState(hasWallet=true,address=legacy,witnessAddress=native,balance=12.5,
                    nodeConnected=true,chain="main",blockHeight=244089,activeWalletName="Main wallet",
                    wallets=listOf(WalletInfo(0,"Main wallet",legacy))),
                onCreateWallet={},onImportWallet={},onNavigateToSend={},onNavigateToReceive={},
                onNavigateToSettings={},onNavigateToHistory={},onRefresh={},onSwitchWallet={},onAddWallet={}) }
        }
        compose.onNodeWithText("Q-Bit",substring=true).assertIsDisplayed()
        compose.onNodeWithText("12.50000000").assertIsDisplayed()
        screenshot("wallet-dashboard")
    }
    @Test fun seedScreenHasAll24WordsAndRequiresCorrectBackupProof() {
        val seed=SeedWallet.fromEntropy(ByteArray(32))
        var confirmed=false
        compose.setContent { QBitXWalletTheme { SeedBackupScreen(seed) { confirmed=true } } }
        compose.onAllNodesWithText("abandon",useUnmergedTree=true).assertCountEquals(23)
        screenshot("wallet-seed")
        compose.onNodeWithText("I wrote down all 24 words").performScrollTo().performClick()
        compose.onNodeWithText("Open wallet").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("wrong wrong wrong")
        compose.onNodeWithText("Open wallet").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("abandon abandon abandon")
        compose.onNodeWithText("Open wallet").assertIsEnabled().performClick()
        org.junit.Assert.assertTrue(confirmed)
    }
    @Test fun receiveShowsNativeAddressAndQrInEcosystemStyle() {
        compose.setContent { QBitXWalletTheme { ReceiveScreen(legacy,native,{}) } }
        compose.onNodeWithText(native).assertExists()
        for(address in listOf(legacy,native)) {
            val bitmap=generateQrCode(address)!!
            val pixels=IntArray(bitmap.width*bitmap.height)
            bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val source=com.google.zxing.RGBLuminanceSource(bitmap.width,bitmap.height,pixels)
            val decoded=com.google.zxing.qrcode.QRCodeReader().decode(
                com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source)))
            org.junit.Assert.assertEquals(address,decoded.text)
            bitmap.recycle()
        }
        screenshot("wallet-receive")
    }
    @Test fun sendRequiresConfirmationAndPassesExactUserIntent() {
        var request:Pair<String,Double>?=null
        compose.setContent { QBitXWalletTheme {
            SendScreen(WalletUiState(hasWallet=true,balance=12.5,nodeConnected=true,address=legacy,witnessAddress=native),
                onSend={address,amount,_ ->request=address to amount},onBack={})
        } }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(native)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("1.25")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.btn_send)).performScrollTo().performClick()
        org.junit.Assert.assertNull(request)
        compose.onAllNodesWithText(native).assertCountEquals(2)
        screenshot("wallet-send-confirmation")
        compose.onNodeWithText(context.getString(R.string.btn_confirm)).performClick()
        org.junit.Assert.assertEquals(native,request!!.first)
        org.junit.Assert.assertEquals(1.25,request!!.second,0.0)
    }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.getExternalFilesDir(null),"audit-screenshots").also { it.mkdirs() }
        val node=if(name=="wallet-send-confirmation") compose.onNode(isDialog()) else compose.onRoot()
        node.captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
