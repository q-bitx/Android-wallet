package org.qbitx.wallet

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import org.qbitx.wallet.ui.*
import org.qbitx.wallet.ui.theme.QBitXWalletTheme

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LanguagePreferences.wrap(base))
    }
    private var authenticated by mutableStateOf(false)
    private var authenticationPending = false
    private val credentialResult = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authenticationPending = false
        authenticated = result.resultCode == RESULT_OK
        if (!authenticated) finish()
    }

    override fun onStart() {
        super.onStart()
        if(LanguagePreferences.selected(this)==null) return
        if (!authenticated && !authenticationPending) {
            val keyguard = getSystemService(android.app.KeyguardManager::class.java)
            if (!keyguard.isDeviceSecure) {
                android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.protect_wallet_title)
                    .setMessage(R.string.protect_wallet_message)
                    .setCancelable(false).setPositiveButton(R.string.close_button) { _, _ -> finish() }.show()
            } else {
                authenticationPending = true
                credentialResult.launch(keyguard.createConfirmDeviceCredentialIntent("Q-BitX", getString(R.string.lock_biometric_subtitle)))
            }
        }
    }

    override fun onStop() {
        if (!authenticationPending) authenticated = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            QBitXWalletTheme {
                var choosingLanguage by remember { mutableStateOf(false) }
                if (LanguagePreferences.selected(this)==null || choosingLanguage) {
                    LanguageScreen { tag -> LanguagePreferences.select(this,tag); recreate() }
                    return@QBitXWalletTheme
                }
                if (!authenticated) return@QBitXWalletTheme
                val viewModel: WalletViewModel = viewModel()
                val state by viewModel.uiState.collectAsState()
                var currentScreen by remember { mutableStateOf("home") }
                var selectedReceiveAddress by remember { mutableStateOf("") }
                if (state.pendingSendSummary != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { viewModel.approveSend(false) },
                        title = { androidx.compose.material3.Text(getString(R.string.payment_title)) },
                        text = { androidx.compose.material3.Text(state.pendingSendSummary!!) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { viewModel.approveSend(true) }) {
                                androidx.compose.material3.Text(getString(R.string.payment_send))
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { viewModel.approveSend(false) }) {
                                androidx.compose.material3.Text(getString(R.string.btn_cancel))
                            }
                        }
                    )
                }

                if (state.pendingSeed != null && !state.isLocked) {
                    SeedBackupScreen(state.pendingSeed!!, onConfirmed = { viewModel.confirmSeed() })
                } else if (state.isLocked) {
                    LockScreen(
                        onPinVerify = { pin -> viewModel.verifyPin(pin) },
                        onUnlocked = { viewModel.unlock() }
                    )
                } else {
                    BackHandler(enabled = currentScreen != "home") {
                        currentScreen = "home"
                    }
                    when (currentScreen) {
                        "home" -> WalletScreen(
                            state = state,
                            onCreateWallet = { viewModel.createWallet() },
                            onImportWallet = { backup -> viewModel.importWallet(backup) },
                            onNavigateToSend = { currentScreen = "send" },
                            onNavigateToReceive = { currentScreen = "receive" },
                            onNavigateToAddresses = { currentScreen = "addresses"; viewModel.refreshAddressBalances() },
                            onNavigateToSettings = { currentScreen = "settings" },
                            onNavigateToHistory = { currentScreen = "history" },
                            onRefresh = { viewModel.refreshBalance() },
                            onSwitchWallet = { id -> viewModel.switchWallet(id) },
                            onAddWallet = { name -> viewModel.createWallet(name) }
                        )
                        "send" -> SendScreen(
                            state = state,
                            onSend = { addr, amt, fee -> viewModel.sendQBX(addr, amt, fee) },
                            onBack = { currentScreen = "home" }
                        )
                        "receive" -> ReceiveScreen(
                            address = state.address,
                            witnessAddress = state.witnessAddress,
                            addresses = walletReceiveAddresses(state.wallets.filter { it.address == state.address }),
                            onBack = { currentScreen = "home" }
                        )
                        "addresses" -> {
                            androidx.compose.runtime.DisposableEffect(Unit) {
                                onDispose { viewModel.cancelAddressBalances() }
                            }
                            AddressListScreen(
                            state = state,
                            onRefresh = { viewModel.refreshAddressBalances() },
                            onReceive = { selectedReceiveAddress = it; currentScreen = "receive-address" },
                            onBack = { currentScreen = "home" }
                        )
                        }
                        "receive-address" -> ReceiveScreen(address = selectedReceiveAddress,
                            addresses = listOf(selectedReceiveAddress), onBack = { currentScreen = "addresses" })
                        "history" -> HistoryScreen(
                            state = state,
                            onBack = { currentScreen = "home" }
                        )
                        "settings" -> SettingsScreen(
                            state = state,
                            onConnect = { url -> viewModel.connectToNode(url) },
                            onExportBackup = { viewModel.exportBackup() },
                            onExportWalletDat = { viewModel.exportWalletDat() },
                            onChooseLanguage = { choosingLanguage=true },
                            onImportWallet = { viewModel.importWallet(it) },
                            onDeleteWallet = {
                                viewModel.deleteWallet()
                                currentScreen = "home"
                            },
                            onBack = { currentScreen = "home" },
                            onSetPin = { pin -> viewModel.setPin(pin) },
                            onRemovePin = { viewModel.removePin() },
                            onRenameWallet = { name -> viewModel.renameWallet(name) },
                            onDeleteActiveWallet = {
                                viewModel.deleteActiveWallet()
                                if (!viewModel.uiState.value.hasWallet) currentScreen = "home"
                            },
                            onSwitchWallet = { id -> viewModel.switchWallet(id) },
                            onAddWallet = { name -> viewModel.createWallet(name) }
                        )
                    }
                }
            }
        }
    }
}
