package org.qbitx.wallet.ui

import android.app.Application
import org.qbitx.wallet.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.qbitx.wallet.crypto.AddressUtils
import org.qbitx.wallet.crypto.SendPlanner
import org.qbitx.wallet.crypto.TransactionBuilder
import org.qbitx.wallet.crypto.TransactionPolicy
import org.qbitx.wallet.data.KeyManager
import org.qbitx.wallet.data.TxRecord
import org.qbitx.wallet.data.WalletInfo
import org.qbitx.wallet.network.NodeRpcClient
import org.qbitx.wallet.network.RpcException
import org.qbitx.wallet.network.TxDetail
import org.qbitx.wallet.network.FeeRates

data class WalletUiState(
    val pendingSendSummary: String? = null,
    val hasWallet: Boolean = false,
    val pendingSeed: String? = null,
    val address: String = "",
    val witnessAddress: String = "",
    val addressBalances: Map<String, Double> = emptyMap(),
    val addressBalancesLoading: Boolean = false,
    val addressBalancesError: String? = null,
    val balance: Double = 0.0,
    val unconfirmedBalance: Double = 0.0,
    val immatureBalance: Double = 0.0,
    val immatureBlocks: Int = 0,
    val nodeConnected: Boolean = false,
    val chain: String = "",
    val blockHeight: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastTxId: String? = null,
    val rpcUrl: String = "https://rpc.qbitx.org/",
    val wallets: List<WalletInfo> = emptyList(),
    val activeWalletName: String = "",
    val isLocked: Boolean = false,
    val hasPin: Boolean = false,
    val txHistory: List<TxRecord> = emptyList(),
    val qbxPriceUsdt: Double? = null,
    val isScanningHistory: Boolean = false,
    val scanProgressText: String? = null,
    val feeLow: Double = 1.0,
    val feeNormal: Double = 5.0,
    val feeHigh: Double = 15.0,
    val sendProgressCurrent: Int = 0,
    val sendProgressTotal: Int = 0,
    val sendProgressPhase: String = ""
)

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private fun message(id: Int,vararg args: Any?) = LanguagePreferences.wrap(getApplication<Application>()).getString(id,*args)

    private val keyManager = KeyManager(application)
    val rpcClient = NodeRpcClient(rpcUrl = "https://rpc.qbitx.org/")
    private var isRefreshing = false
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    private fun cancelBalanceRefresh() {
        refreshGeneration++
        refreshJob?.cancel()
        refreshJob=null
        isRefreshing=false
    }
    private fun resetAccountBalance() {
        _uiState.value=_uiState.value.copy(balance=0.0,unconfirmedBalance=0.0,
            immatureBalance=0.0,immatureBlocks=0,isScanningHistory=false,scanProgressText=null)
    }
    private var paymentInProgress = false
    private var sendApproval: kotlinx.coroutines.CompletableDeferred<Boolean>? = null
    fun approveSend(approved: Boolean) { sendApproval?.complete(approved) }

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        val hasPIN = keyManager.hasPin()
        _uiState.value = _uiState.value.copy(isLocked = hasPIN, hasPin = hasPIN)
        val savedUrl = keyManager.getSavedRpcUrl()
        if (!savedUrl.isNullOrEmpty()) {
            try {
                val normalizedUrl = NodeRpcClient.normalizeRpcUrl(savedUrl)
                _uiState.value = _uiState.value.copy(rpcUrl = normalizedUrl)
                rpcClient.configure(normalizedUrl)
            } catch (_: Exception) {
                keyManager.saveRpcUrl("https://rpc.qbitx.org/")
            }
        }
        try { checkWallet() } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(error = message(R.string.wallet_integrity_error))
        }
        autoConnect()
    }

    private fun autoConnect() {
        viewModelScope.launch {
            try {
                val info = rpcClient.getBlockchainInfo()
                _uiState.value = _uiState.value.copy(
                    nodeConnected = true, chain = info.chain, blockHeight = info.blocks
                )
                refreshFeeRates()
                refreshBalance()
            } catch (_: Exception) {}
        }
    }

    private fun checkWallet() {
        val has = keyManager.hasWallet()
        val address = keyManager.getAddress() ?: ""
        // Native PQ witness address (dil1q...) of the same key
        val witnessAddress = keyManager.getPublicKey()?.let { AddressUtils.pubkeyToWitnessAddress(it) } ?: ""
        val wallets = keyManager.listWallets()
        val activeId = keyManager.getActiveWalletId()
        val activeName = wallets.find { it.id == activeId }?.name ?: ""
        val txHistory = keyManager.getTxHistoryForActiveWallet()
        val pendingDelta = pendingOutDelta(txHistory)
        _uiState.value = _uiState.value.copy(
            hasWallet = has, pendingSeed = keyManager.pendingSeed(), address = address, witnessAddress = witnessAddress,
            wallets = wallets, activeWalletName = activeName,
            txHistory = txHistory,
            unconfirmedBalance = pendingDelta
        )
    }

    private fun pendingOutDelta(history: List<TxRecord>): Double = HistoryState.pendingOutgoing(history)

    fun createWallet(name: String = "") {
        if (paymentInProgress || _uiState.value.isLoading) return
        cancelBalanceRefresh()
        val previousId=keyManager.getActiveWalletId()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { keyManager.createWallet(name) }
                checkWallet()
                if(previousId!=keyManager.getActiveWalletId()) resetAccountBalance()
                _uiState.value = _uiState.value.copy(isLoading = false)
                refreshBalance()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = message(R.string.create_wallet_error,e.message)
                )
            }
        }
    }

    fun switchWallet(id: Int) {
        if (paymentInProgress || _uiState.value.isLoading) return
        // Cancel any ongoing refresh to prevent stale data overwriting new wallet
        cancelBalanceRefresh()
        keyManager.setActiveWallet(id)
        checkWallet()
        _uiState.value = _uiState.value.copy(
            balance = 0.0, unconfirmedBalance = 0.0,
            immatureBalance = 0.0, immatureBlocks = 0,
            isScanningHistory = false, scanProgressText = null
        )
        viewModelScope.launch { refreshBalance() }
    }

    fun renameWallet(newName: String) {
        val id = keyManager.getActiveWalletId()
        if (id >= 0) {
            keyManager.renameWallet(id, newName)
            checkWallet()
        }
    }

    fun deleteActiveWallet() {
        if (paymentInProgress || _uiState.value.isLoading) return
        val id = keyManager.getActiveWalletId()
        if (id >= 0) {
            cancelBalanceRefresh()
            keyManager.deleteWallet(id)
            checkWallet()
            _uiState.value = _uiState.value.copy(
                balance = 0.0, unconfirmedBalance = 0.0,
                immatureBalance = 0.0, immatureBlocks = 0,
                isScanningHistory = false, scanProgressText = null
            )
            if (_uiState.value.hasWallet) {
                viewModelScope.launch { refreshBalance() }
            }
        }
    }

    fun connectToNode(rpcUrl: String) {
        if (paymentInProgress) return
        viewModelScope.launch {
            try {
                val normalizedUrl = NodeRpcClient.normalizeRpcUrl(rpcUrl)
                _uiState.value = _uiState.value.copy(isLoading = true, error = null, rpcUrl = normalizedUrl)
                rpcClient.configure(normalizedUrl)
                val info = rpcClient.getBlockchainInfo()
                keyManager.saveRpcUrl(normalizedUrl)
                _uiState.value = _uiState.value.copy(
                    nodeConnected = true, chain = info.chain,
                    blockHeight = info.blocks, isLoading = false
                )
                refreshFeeRates()
                refreshBalance()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    nodeConnected = false, isLoading = false,
                    error = message(R.string.connect_error,e.message)
                )
            }
        }
    }

    private var addressBalanceJob: Job? = null

    fun cancelAddressBalances() {
        addressBalanceJob?.cancel()
        addressBalanceJob = null
        _uiState.value = _uiState.value.copy(addressBalancesLoading = false)
    }

    /** Explicit, read-only scans. Serial scans avoid concurrent scantxoutset on the node. */
    fun refreshAddressBalances() {
        if (addressBalanceJob?.isActive == true) return
        val snapshot = walletReceiveAddresses(_uiState.value.wallets)
        val url = _uiState.value.rpcUrl
        addressBalanceJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(addressBalancesLoading = true,
                addressBalances = emptyMap(), addressBalancesError = null)
            try {
                refreshJob?.join()
                for (address in snapshot) {
                    val scan = rpcClient.scanTxOutSet(address)
                    if (walletReceiveAddresses(_uiState.value.wallets) != snapshot || _uiState.value.rpcUrl != url) return@launch
                    _uiState.value = _uiState.value.copy(addressBalances =
                        _uiState.value.addressBalances + (address to (scan.totalAmount - scan.immatureAmount)))
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(addressBalancesError = e.message ?: "RPC error")
            } finally {
                _uiState.value = _uiState.value.copy(addressBalancesLoading = false)
            }
        }
    }

    fun refreshBalance() {
        if (paymentInProgress || _uiState.value.isLoading || _uiState.value.isLocked || isRefreshing || addressBalanceJob?.isActive == true) return
        val generation=++refreshGeneration
        refreshJob = viewModelScope.launch {
            isRefreshing = true
            val walletId = keyManager.getActiveWalletId()
            try {
                val address = _uiState.value.address

                // Launch price fetch and blockchain info concurrently
                val priceDeferred = async { rpcClient.fetchQbxPriceUsdt() }
                val infoDeferred = async { rpcClient.getBlockchainInfo() }

                if (address.isNotEmpty()) {
                    val scanResult = rpcClient.scanTxOutSet(address, _uiState.value.witnessAddress)
                    if (keyManager.getActiveWalletId() != walletId || generation!=refreshGeneration) return@launch
                    val spendable = scanResult.totalAmount - scanResult.immatureAmount
                    val pendingDelta = pendingOutDelta(_uiState.value.txHistory)
                    _uiState.value = _uiState.value.copy(
                        balance = spendable, error = null, nodeConnected = true,
                        unconfirmedBalance = pendingDelta,
                        immatureBalance = scanResult.immatureAmount,
                        immatureBlocks = scanResult.immatureBlocks
                    )
                    scanTxHistory(address, scanResult.unspents, generation)
                }

                val info = infoDeferred.await()
                _uiState.value = _uiState.value.copy(blockHeight = info.blocks)

                val price = priceDeferred.await()
                if (price != null) {
                    _uiState.value = _uiState.value.copy(qbxPriceUsdt = price)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                if(walletId==keyManager.getActiveWalletId() && generation==refreshGeneration)
                    _uiState.value = _uiState.value.copy(error = message(R.string.balance_refresh_error,e.message), nodeConnected = false)
            } finally {
                if(generation==refreshGeneration) isRefreshing = false
            }
        }
    }

    /**
     * Fetch TX details and build TxRecords.
     * Revalidates confirmed and pending history using saved block hashes and UTXO heights.
     */
    private suspend fun fetchTxRecords(
        txids: List<String>,
        localByTxid: Map<String, TxRecord>,
        myAddress: String,
        walletId: Int,
        knownHeights: Map<String,Int> = emptyMap()
    ): List<TxRecord> {
        val txidsToFetch = txids
        if (txidsToFetch.isEmpty()) return emptyList()
        val myWitnessAddress = _uiState.value.witnessAddress

        val semaphore = Semaphore(8)
        val fetchedDetails: List<Pair<String, TxDetail?>> = coroutineScope {
            txidsToFetch.map { txid ->
                async {
                    semaphore.acquire()
                    try {
                        txid to rpcClient.getTransactionDetails(txid,knownHeights[txid],localByTxid[txid]?.blockHash)
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }

        val records = mutableListOf<TxRecord>()

        for ((txid, detail) in fetchedDetails) {
            val local = localByTxid[txid]

            if (detail == null) {
                if (local != null) records.add(HistoryState.reconcile(local,null))
                continue
            }

            var receivedAmount = 0.0
            var sentToOther = 0.0
            var otherAddress = ""

            for (vo in detail.voutList) {
                if (vo.addresses.contains(myAddress) ||
                    (myWitnessAddress.isNotEmpty() && vo.addresses.contains(myWitnessAddress))
                ) {
                    receivedAmount += vo.value
                } else {
                    sentToOther += vo.value
                    if (otherAddress.isEmpty() && vo.addresses.isNotEmpty()) {
                        otherAddress = vo.addresses.first()
                    }
                }
            }

            val timestamp = if (detail.time > 0) detail.time * 1000 else
                local?.timestamp ?: System.currentTimeMillis()

            val isSender = if (local != null) {
                local.direction == "out"
            } else {
                rpcClient.isAddressSpender(detail, myAddress) ||
                    (myWitnessAddress.isNotEmpty() && rpcClient.isAddressSpender(detail, myWitnessAddress))
            }

            if (isSender) {
                if (local != null) {
                    records.add(HistoryState.reconcile(local,detail))
                } else {
                    records.add(
                        TxRecord(
                            txid = txid,
                            toAddress = otherAddress,
                            amount = sentToOther,
                            fee = "",
                            timestamp = timestamp,
                            walletId = walletId,
                            direction = "out",
                            confirmations = detail.confirmations,
                            blockHash = detail.blockHash
                        )
                    )
                }
            } else {
                if (receivedAmount > 0) {
                    records.add(
                        TxRecord(
                            txid = txid,
                            toAddress = myAddress,
                            amount = receivedAmount,
                            fee = "",
                            timestamp = timestamp,
                            walletId = walletId,
                            direction = "in",
                            confirmations = detail.confirmations,
                            blockHash = detail.blockHash
                        )
                    )
                }
            }
        }

        return records
    }

    /**
     * Scan blockchain for TX history using UTXO txids.
     * Phase 1: Immediately show TXs from UTXOs + local history.
     * Phase 2: Scan blockchain for additional TXs (incremental, may take minutes).
     */
    private suspend fun scanTxHistory(myAddress: String, utxos: List<org.qbitx.wallet.network.Utxo>, generation: Long) {
        val utxoTxids = utxos.map { it.txid }.distinct()
        val knownHeights = utxos.filter { it.height>=0 }.associate { it.txid to it.height }
        val walletId = keyManager.getActiveWalletId()
        val localHistory = keyManager.getTxHistoryForActiveWallet()
        val localByTxid = localHistory.associateBy { it.txid }

        // Phase 1: Immediately fetch and show UTXO-based + local TXs
        val baseTxids = (utxoTxids + localHistory.map { it.txid }).distinct()
        if (baseTxids.isNotEmpty()) {
            try {
                val baseRecords = fetchTxRecords(baseTxids, localByTxid, myAddress, walletId, knownHeights)
                // Re-read storage to preserve TXs added during processing (e.g. from sendQBX)
                val latestLocal = keyManager.getTxHistoryForActiveWallet()
                val resultByTxid = baseRecords.associateBy { it.txid }
                val merged = baseRecords.toMutableList()
                for (tx in latestLocal) {
                    if (tx.txid !in resultByTxid) {
                        merged.add(tx)
                    }
                }
                val sorted = merged.sortedByDescending { it.timestamp }
                // Only write if still on the same wallet (guard against cancelled/stale jobs)
                if (keyManager.getActiveWalletId() == walletId && generation == refreshGeneration) {
                    keyManager.replaceTxHistoryForWallet(sorted, walletId)
                    _uiState.value = _uiState.value.copy(txHistory = sorted, unconfirmedBalance = pendingOutDelta(sorted))

                    // Clear pending-spent outpoints once all our recent out-TXs
                    // are confirmed (>= 1 confirmation). Keeps the lockset from
                    // growing forever. If any TX is still unconfirmed, leave it.
                    if (HistoryState.canReleasePendingLocks(sorted)) {
                        keyManager.clearPendingSpentOutpoints(walletId)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (_: Exception) {}
        }

        // Phase 2: Scan blockchain for additional TXs (incremental)
        try {
            val blockHeight = _uiState.value.blockHeight
            var lastScanned = keyManager.getLastScannedHeight(walletId)

            // Detect TX data loss and force full rescan when needed
            val lastKnownCount = keyManager.getLastScanTxCount(walletId)
            if (lastScanned > 0 && (localHistory.isEmpty() || lastKnownCount < 0 || localHistory.size < lastKnownCount)) {
                lastScanned = 0
                keyManager.setLastScannedHeight(walletId, 0)
            }

            if (blockHeight > lastScanned) {
                val totalBlocks = blockHeight - lastScanned
                if (totalBlocks > 100) {
                    _uiState.value = _uiState.value.copy(
                        isScanningHistory = true,
                        scanProgressText = message(R.string.scanning_history)
                    )
                }
                val (txids, highestScanned) = rpcClient.discoverAllTxIds(
                    myAddress,
                    blockHeight = blockHeight,
                    lastScannedHeight = lastScanned,
                    onProgress = { scanned, total ->
                        val pct = if (total > 0) scanned * 100 / total else 0
                        _uiState.value = _uiState.value.copy(
                            scanProgressText = message(R.string.scanning_blocks,pct)
                        )
                    }
                )
                _uiState.value = _uiState.value.copy(
                    isScanningHistory = false,
                    scanProgressText = null
                )
                if (highestScanned > lastScanned && keyManager.getActiveWalletId() == walletId && generation == refreshGeneration) {
                    keyManager.setLastScannedHeight(walletId, highestScanned)
                }

                // Fetch details for newly discovered TXs
                val newTxids = txids.filter { it !in baseTxids }
                if (newTxids.isNotEmpty() && keyManager.getActiveWalletId() == walletId && generation == refreshGeneration) {
                    val currentHistory = keyManager.getTxHistoryForActiveWallet()
                    val currentByTxid = currentHistory.associateBy { it.txid }
                    val extraTxids = newTxids.filter { it !in currentByTxid }
                    if (extraTxids.isNotEmpty()) {
                        val newRecords = fetchTxRecords(extraTxids, currentByTxid, myAddress, walletId)
                        if (keyManager.getActiveWalletId()!=walletId || generation!=refreshGeneration) return
                        val combined = (currentHistory + newRecords).sortedByDescending { it.timestamp }
                        keyManager.replaceTxHistoryForWallet(combined, walletId)
                        _uiState.value = _uiState.value.copy(txHistory = combined, unconfirmedBalance = pendingOutDelta(combined))
                    }
                }

                // Save TX count after successful scan for data-loss detection
                if (keyManager.getActiveWalletId() == walletId && generation == refreshGeneration) {
                    keyManager.setLastScanTxCount(walletId, keyManager.getTxHistoryForActiveWallet().size)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(
                isScanningHistory = false,
                scanProgressText = null
            )
        }
    }

    private fun refreshFeeRates() {
        viewModelScope.launch {
            try {
                val (low, normal, high) = rpcClient.fetchFeeRates()
                _uiState.value = _uiState.value.copy(
                    feeLow = low, feeNormal = normal, feeHigh = high
                )
            } catch (_: Exception) { /* keep defaults */ }
        }
    }

    fun sendQBX(toAddress: String, amount: Double, feePolicy: String = "normal") {
        if (paymentInProgress || _uiState.value.isLoading || _uiState.value.isLocked || _uiState.value.pendingSeed != null) return
        paymentInProgress = true
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, lastTxId = null)
            try {
                require(amount.isFinite() && amount > 0) { message(R.string.invalid_amount) }
                val endpointInfo = try {
                    rpcClient.getBlockchainInfo()
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(nodeConnected = false)
                    throw Exception(message(R.string.no_connection))
                }

                _uiState.value = _uiState.value.copy(
                    nodeConnected = true,
                    chain = endpointInfo.chain,
                    blockHeight = endpointInfo.blocks
                )

                // Validate address via node
                if (!rpcClient.validateAddress(toAddress)) {
                    throw Exception(message(R.string.invalid_recipient))
                }

                val myAddress = _uiState.value.address
                val witnessAddress = _uiState.value.witnessAddress
                val publicKey = keyManager.getPublicKey()
                    ?: throw Exception(message(R.string.no_wallet))

                val scanResult = rpcClient.scanTxOutSet(myAddress, witnessAddress)
                val pendingSpent = keyManager.getPendingSpentOutpoints()
                val spendable = scanResult.unspents
                    .filter { !(it.isCoinbase && it.confirmations < 100) }
                    .filter { "${it.txid}:${it.vout}" !in pendingSpent }
                if (spendable.isEmpty()) {
                    if (scanResult.unspents.isNotEmpty()) {
                        throw Exception("Alle UTXOs sind unreife Coinbase-Belohnungen (noch ${scanResult.immatureBlocks} Bl\u00f6cke)")
                    }
                    throw Exception("Keine verf\u00fcgbaren UTXOs")
                }

                val state = _uiState.value
                val feeRate = FeeRates.forPolicy(feePolicy,state.feeLow,state.feeNormal,state.feeHigh)
                val amountSat = java.math.BigDecimal.valueOf(amount).movePointRight(8).longValueExact()

                data class PlannedBatch(
                    val inputs: List<org.qbitx.wallet.network.Utxo>,
                    val sendAmtSat: Long,
                    val changeSat: Long,
                    val actualFeeSat: Long
                )

                data class PreparedBatch(
                    val signedHex: String,
                    val sendAmtSat: Long,
                    val actualFeeSat: Long
                )

                // Preflight the full split plan before sending anything so a failed
                // feasibility check cannot leave the user with partial broadcasts.
                // SendPlanner limits every batch by transaction weight: 4 legacy "M..."
                // inputs or up to 60 native PQ witness "dil1q..." inputs (or a mix).
                val coins = spendable.mapIndexed { i, u ->
                    SendPlanner.Coin(
                        amountSat = Math.round(u.amount * 1e8),
                        isWitness = AddressUtils.isWitnessScriptPubKey(AddressUtils.fromHex(u.scriptPubKey)),
                        ref = i
                    )
                }
                val plannedBatches = try {
                    SendPlanner.plan(coins, amountSat, feeRate).map { b ->
                        PlannedBatch(
                            inputs = b.coinRefs.map { spendable[it] },
                            sendAmtSat = b.sendSat,
                            changeSat = b.changeSat,
                            actualFeeSat = b.feeSat
                        )
                    }
                } catch (e: SendPlanner.PlanException) {
                    val missingQbx = "%.8f".format(e.missingSat / 1e8)
                    throw Exception(
                        when (e.failure) {
                            SendPlanner.Failure.INSUFFICIENT_FUNDS ->
                                "Nicht genug Guthaben (benötigt: $missingQbx QBX plus Gebühren)."
                            SendPlanner.Failure.UTXOS_TOO_SMALL ->
                                "Saldo besteht aus zu vielen winzigen UTXOs — bitte höhere Gebühr wählen oder kleinere Beträge senden."
                        }
                    )
                }

                // Change goes to the wallet's own native PQ witness address: outputs there
                // are far cheaper to spend later than legacy ones.
                val changeAddress = if (witnessAddress.isNotEmpty()) witnessAddress else myAddress

                require(plannedBatches.size == 1) { message(R.string.multiple_transactions) }
                val totalBatches = plannedBatches.size
                _uiState.value = _uiState.value.copy(
                    sendProgressCurrent = 0,
                    sendProgressTotal = totalBatches,
                    sendProgressPhase = message(R.string.signing_progress)
                )

                val preparedBatches = plannedBatches.mapIndexed { idx, batch ->
                    _uiState.value = _uiState.value.copy(
                        sendProgressCurrent = idx + 1,
                        sendProgressTotal = totalBatches,
                        sendProgressPhase = message(R.string.signing_progress)
                    )
                    // Sending to our own change address: one combined output, because
                    // createrawtransaction rejects the same address twice.
                    val changeToRecipient = batch.changeSat > 0 && toAddress.trim() == changeAddress
                    val recipientSat = if (changeToRecipient) batch.sendAmtSat + batch.changeSat else batch.sendAmtSat
                    val recipientAmountQbx = recipientSat / 1e8
                    val changeAmountQbx = if (batch.changeSat > 0 && !changeToRecipient) batch.changeSat / 1e8 else null

                    val unsignedHex = rpcClient.createRawTransaction(
                        inputs = batch.inputs,
                        recipientAddress = toAddress,
                        recipientAmount = recipientAmountQbx,
                        changeAddress = if (changeAmountQbx != null) changeAddress else null,
                        changeAmount = changeAmountQbx
                    )

                    val scriptPubKeys = mutableMapOf<Int, String>()
                    val inputAmountsSat = mutableMapOf<Int, Long>()
                    batch.inputs.forEachIndexed { i, utxo ->
                        scriptPubKeys[i] = utxo.scriptPubKey
                        inputAmountsSat[i] = Math.round(utxo.amount * 1e8)
                    }

                    TransactionPolicy.verify(unsignedHex, batch.inputs.map { it.txid to it.vout },
                        toAddress, recipientSat, if (changeAmountQbx != null) changeAddress else null,
                        if (changeAmountQbx != null) batch.changeSat else 0L, batch.actualFeeSat,
                        batch.inputs.map { java.math.BigDecimal.valueOf(it.amount).movePointRight(8).longValueExact() }, publicKey,
                        batch.inputs.map { it.scriptPubKey })
                    PreparedBatch(
                        signedHex = TransactionBuilder.signTransaction(
                            unsignedTxHex = unsignedHex,
                            utxoScriptPubKeys = scriptPubKeys,
                            signFn = { hash -> keyManager.sign(hash) },
                            publicKey = publicKey,
                            utxoAmountsSat = inputAmountsSat
                        ),
                        sendAmtSat = batch.sendAmtSat,
                        actualFeeSat = batch.actualFeeSat
                    )
                }

                val sentTxids = mutableListOf<String>()
                val approval = kotlinx.coroutines.CompletableDeferred<Boolean>()
                sendApproval = approval
                val exactFee = java.math.BigDecimal.valueOf(preparedBatches.sumOf { it.actualFeeSat }, 8).toPlainString()
                _uiState.value = _uiState.value.copy(pendingSendSummary =
                    message(R.string.payment_summary,toAddress,amount.toString(),exactFee))
                val approved = try { kotlinx.coroutines.withTimeout(120_000) { approval.await() } }
                    finally {
                        sendApproval = null
                        _uiState.value = _uiState.value.copy(pendingSendSummary = null)
                    }
                require(approved) { message(R.string.payment_cancelled) }
                val pendingRecords = mutableListOf<TxRecord>()
                var totalActualFeeSat = 0L
                val sendStart = System.currentTimeMillis()
                val activeWalletId = keyManager.getActiveWalletId()

                _uiState.value = _uiState.value.copy(
                    sendProgressCurrent = 0,
                    sendProgressTotal = totalBatches,
                    sendProgressPhase = "Sende"
                )

                preparedBatches.forEachIndexed { index, batch ->
                    _uiState.value = _uiState.value.copy(
                        sendProgressCurrent = index + 1,
                        sendProgressTotal = totalBatches,
                        sendProgressPhase = "Sende"
                    )
                    val spentOutpoints = plannedBatches[index].inputs.map { "${it.txid}:${it.vout}" }
                    keyManager.reserveBroadcast(spentOutpoints,batch.signedHex,activeWalletId)
                    val txid = try { rpcClient.sendRawTransaction(batch.signedHex) } catch (e: Exception) {
                        throw Exception(message(R.string.uncertain_broadcast,e.message))
                    }
                    sentTxids.add(txid)
                    totalActualFeeSat += batch.actualFeeSat

                    // Lock the inputs we just spent so the next send (even on a
                    // restart) won't reuse them while they sit in the mempool.
                    keyManager.addPendingSpentOutpoints(spentOutpoints, activeWalletId)

                    val partAmountQbx = batch.sendAmtSat / 1e8
                    val partFeeQbx = batch.actualFeeSat / 1e8
                    val partFeeStr = "%.8f QBX".format(partFeeQbx)

                    keyManager.addTxRecord(txid, toAddress, partAmountQbx, partFeeStr)
                    keyManager.acknowledgeBroadcast(spentOutpoints,activeWalletId)

                    pendingRecords.add(
                        TxRecord(
                            txid = txid,
                            toAddress = toAddress,
                            amount = partAmountQbx,
                            fee = partFeeStr,
                            timestamp = sendStart + index,
                            walletId = activeWalletId,
                            direction = "out",
                            confirmations = 0
                        )
                    )
                }

                val feeQbx = totalActualFeeSat / 1e8

                val lastTxid = sentTxids.last()

                // Immediately deduct sent amount + fee from displayed balance.
                val currentBalance = _uiState.value.balance
                val deducted = amount + feeQbx
                val updatedHistory = pendingRecords.sortedByDescending { it.timestamp } +
                    _uiState.value.txHistory.filter { h -> sentTxids.none { it == h.txid } }
                _uiState.value = _uiState.value.copy(
                    isLoading = false, lastTxId = lastTxid,
                    balance = currentBalance - deducted,
                    unconfirmedBalance = -deducted,
                    txHistory = updatedHistory,
                    sendProgressCurrent = 0,
                    sendProgressTotal = 0,
                    sendProgressPhase = ""
                )
            } catch (e: RpcException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, error = e.message,
                    sendProgressCurrent = 0, sendProgressTotal = 0, sendProgressPhase = ""
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, error = message(R.string.send_error,e.message),
                    sendProgressCurrent = 0, sendProgressTotal = 0, sendProgressPhase = ""
                )
            } finally {
                paymentInProgress = false
            }
        }
    }

    fun confirmSeed() { keyManager.confirmSeed(); checkWallet() }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearLastTx() { _uiState.value = _uiState.value.copy(lastTxId = null) }
    fun getPublicKeyHex(): String = keyManager.exportPublicKeyHex() ?: ""
    fun exportBackup(): String = keyManager.exportSeed() ?: keyManager.exportBackup() ?: ""

    fun exportWalletDat(): ByteArray = keyManager.exportWalletDat()

    fun importWallet(backup: String, name: String = "") {
        if (paymentInProgress || _uiState.value.isLoading) return
        cancelBalanceRefresh()
        val previousId=keyManager.getActiveWalletId()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (backup.startsWith("qbx-core-dat:")) {
                    require(backup.length <= org.qbitx.wallet.data.CoreWalletDat.MAX_BYTES * 4 / 3 + 32) { "Wallet file too large" }
                    val bytes=android.util.Base64.decode(backup.removePrefix("qbx-core-dat:"),android.util.Base64.NO_WRAP)
                    try { keyManager.importWalletDat(bytes,name) } finally { bytes.fill(0) }
                } else keyManager.importRecovery(backup, name)
                }
                checkWallet()
                if(previousId!=keyManager.getActiveWalletId()) resetAccountBalance()
                _uiState.value = _uiState.value.copy(isLoading = false)
                refreshBalance()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = message(R.string.import_error,e.message)
                )
            }
        }
    }

    fun deleteWallet() {
        if (paymentInProgress) return
        cancelBalanceRefresh()
        keyManager.deleteAllWallets()
        _uiState.value = WalletUiState(hasPin = keyManager.hasPin())
    }

    // ---- PIN ----

    fun verifyPin(pin: String): Boolean = keyManager.verifyPin(pin)

    fun unlock() {
        _uiState.value = _uiState.value.copy(isLocked = false)
    }

    fun setPin(pin: String) {
        keyManager.setPin(pin)
        _uiState.value = _uiState.value.copy(hasPin = true)
    }

    fun removePin() {
        keyManager.removePin()
        _uiState.value = _uiState.value.copy(hasPin = false)
    }
}
