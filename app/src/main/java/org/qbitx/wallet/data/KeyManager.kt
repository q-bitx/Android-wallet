package org.qbitx.wallet.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.qbitx.wallet.crypto.AddressUtils
import org.qbitx.wallet.crypto.DilithiumNative
import java.security.MessageDigest
import org.qbitx.wallet.crypto.SeedWallet

data class WalletInfo(val id: Int, val name: String, val address: String)

data class TxRecord(
    val txid: String,
    val toAddress: String,
    val amount: Double,
    val fee: String,
    val timestamp: Long,
    val walletId: Int,
    val direction: String = "out",      // "in" or "out"
    val confirmations: Int = -1          // -1 = unknown, 0 = pending
)

class KeyManager(context: Context, preferencesOverride: android.content.SharedPreferences? = null) {

    private val dilithium = DilithiumNative()
    private val gson = Gson()

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = preferencesOverride ?: EncryptedSharedPreferences.create(
        context,
        "qbitx_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init { migrate() }

    /** Migrate v1.0.x single-wallet → v1.1.0 multi-wallet format */
    private fun migrate() {
        val oldPub = prefs.getString("pubkey", null)
        if (oldPub != null && !prefs.contains("wallet_ids")) {
            val oldPriv = prefs.getString("privkey", null) ?: return
            prefs.edit()
                .putString("w0_pub", oldPub)
                .putString("w0_priv", oldPriv)
                .putString("w0_name", "Haupt-Wallet")
                .putString("wallet_ids", "0")
                .putInt("next_id", 1)
                .putInt("active_id", 0)
                .remove("pubkey")
                .remove("privkey")
                .apply()
        }
    }

    // ---- Multi-Wallet ----

    private fun walletIds(): List<Int> {
        val s = prefs.getString("wallet_ids", "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun listWallets(): List<WalletInfo> = walletIds().mapNotNull { id ->
        val b64 = prefs.getString("w${id}_pub", null) ?: return@mapNotNull null
        val pub = Base64.decode(b64, Base64.NO_WRAP)
        val name = prefs.getString("w${id}_name", "Wallet $id") ?: "Wallet $id"
        WalletInfo(id, name, AddressUtils.pubkeyToAddress(pub))
    }

    fun getActiveWalletId(): Int = prefs.getInt("active_id", -1)

    fun setActiveWallet(id: Int) {
        require(id in walletIds()) { "Unknown wallet" }
        checkWalletIntegrity(id)
        check(prefs.edit().putInt("active_id", id).commit())
    }

    fun hasWallet(): Boolean = walletIds().isNotEmpty()

    private val validated = mutableSetOf<Int>()

    private fun checkWalletIntegrity(id: Int) {
        if (id in validated) return
        val pub = Base64.decode(requireNotNull(prefs.getString("w${id}_pub", null)), Base64.NO_WRAP)
        val priv = Base64.decode(requireNotNull(prefs.getString("w${id}_priv", null)), Base64.NO_WRAP)
        try {
            require(pub.size == dilithium.getPublicKeyBytes() && priv.size == dilithium.getSecretKeyBytes()) { "Invalid stored key size" }
            val mnemonic = prefs.getString("w${id}_seed", null)
            if (mnemonic != null) {
                require(prefs.getString("w${id}_derivation", null) == SeedWallet.VERSION) { "Unsupported recovery version" }
                val seed = SeedWallet.keySeed(mnemonic)
                val restored = try { requireNotNull(dilithium.generateKeypairFromSeed(seed)) } finally { seed.fill(0) }
                try {
                    require(MessageDigest.isEqual(pub, restored[0]) && MessageDigest.isEqual(priv, restored[1])) { "Stored seed and keys disagree; restore from your paper backup" }
                } finally { restored[1].fill(0) }
            } else {
                require(!prefs.contains("w${id}_derivation")) { "Recovery metadata is missing its seed" }
                val message = "QBX stored-key integrity v1".toByteArray()
                val signature = requireNotNull(dilithium.sign(message, priv))
                require(dilithium.verify(signature, message, pub)) { "Stored key pair is inconsistent" }
            }
            validated.add(id)
        } finally { priv.fill(0) }
    }

    private fun storeWallet(keys: Array<ByteArray>, mnemonic: String?, name: String, confirmed: Boolean): WalletInfo {
        val pubkey = keys[0]; val privkey = keys[1]
        try {
            require(pubkey.size == dilithium.getPublicKeyBytes() && privkey.size == dilithium.getSecretKeyBytes())
            val duplicate = walletIds().firstOrNull { id ->
                prefs.getString("w${id}_pub", null) == Base64.encodeToString(pubkey, Base64.NO_WRAP)
            }
            if (duplicate != null) {
                if (mnemonic == null) checkWalletIntegrity(duplicate)
                val update = prefs.edit().putInt("active_id", duplicate)
                if (mnemonic != null) update.putString("w${duplicate}_seed", mnemonic)
                    .putString("w${duplicate}_pub",Base64.encodeToString(pubkey,Base64.NO_WRAP))
                    .putString("w${duplicate}_priv",Base64.encodeToString(privkey,Base64.NO_WRAP))
                    .putString("w${duplicate}_derivation", SeedWallet.VERSION).putBoolean("w${duplicate}_seed_confirmed", confirmed)
                check(update.commit()) { "Wallet storage failed" }
                validated.remove(duplicate)
                checkWalletIntegrity(duplicate)
                return listWallets().first { it.id == duplicate }
            }
            val id = prefs.getInt("next_id", 0)
            val walletName = name.ifBlank { "Wallet ${walletIds().size + 1}" }
            val edit = prefs.edit()
                .putString("w${id}_pub", Base64.encodeToString(pubkey, Base64.NO_WRAP))
                .putString("w${id}_priv", Base64.encodeToString(privkey, Base64.NO_WRAP))
                .putString("w${id}_name", walletName)
                .putString("wallet_ids", (walletIds() + id).joinToString(","))
                .putInt("next_id", Math.addExact(id, 1)).putInt("active_id", id)
            if (mnemonic != null) {
                edit.putString("w${id}_seed", mnemonic).putString("w${id}_derivation", SeedWallet.VERSION)
                    .putBoolean("w${id}_seed_confirmed", confirmed)
            }
            // One durable write for keys, seed, format, active ID and wallet list.
            check(edit.commit()) { "Wallet storage failed; no wallet has been acknowledged" }
            validated.add(id)
            return WalletInfo(id, walletName, AddressUtils.pubkeyToAddress(pubkey))
        } finally { privkey.fill(0) }
    }

    fun createWallet(name: String = ""): WalletInfo {
        val mnemonic = SeedWallet.generate()
        val seed = SeedWallet.keySeed(mnemonic)
        val keys = try { requireNotNull(dilithium.generateKeypairFromSeed(seed)) } finally { seed.fill(0) }
        return storeWallet(keys, mnemonic, name, false)
    }

    fun pendingSeed(): String? = if (prefs.getBoolean("w${getActiveWalletId()}_seed_confirmed", false)) null else exportSeed()
    fun confirmSeed() { checkWalletIntegrity(getActiveWalletId()); check(prefs.edit().putBoolean("w${getActiveWalletId()}_seed_confirmed", true).commit()) }
    fun exportSeed(): String? {
        val id = getActiveWalletId(); if (id < 0) return null
        checkWalletIntegrity(id)
        return prefs.getString("w${id}_seed", null)
    }

    fun restoreSeed(text: String, name: String = ""): WalletInfo {
        val mnemonic = SeedWallet.validate(text)
        val seed = SeedWallet.keySeed(mnemonic)
        val keys = try { requireNotNull(dilithium.generateKeypairFromSeed(seed)) } finally { seed.fill(0) }
        return storeWallet(keys, mnemonic, name, true)
    }

    fun importRecovery(backup: String, name: String = ""): WalletInfo {
        require(backup.length <= 20000) { "Backup is too large" }
        val input = backup.trim()
        return when {
            input.startsWith("{") -> restoreSeed(SeedWallet.readBackupEnvelope(input), name)
            input.contains(":") -> importWallet(input, name)
            else -> restoreSeed(input, name)
        }
    }

    fun renameWallet(id: Int, newName: String) {
        prefs.edit().putString("w${id}_name", newName).apply()
    }

    fun getAddress(): String? {
        val id = getActiveWalletId(); if (id < 0) return null
        checkWalletIntegrity(id)
        val b64 = prefs.getString("w${id}_pub", null) ?: return null
        return AddressUtils.pubkeyToAddress(Base64.decode(b64, Base64.NO_WRAP))
    }

    fun getPublicKey(): ByteArray? {
        val id = getActiveWalletId(); if (id < 0) return null
        checkWalletIntegrity(id)
        val b64 = prefs.getString("w${id}_pub", null) ?: return null
        return Base64.decode(b64, Base64.NO_WRAP)
    }

    fun sign(message: ByteArray): ByteArray? {
        val id = getActiveWalletId(); if (id < 0) return null
        checkWalletIntegrity(id)
        val b64 = prefs.getString("w${id}_priv", null) ?: return null
        val priv = Base64.decode(b64, Base64.NO_WRAP)
        return try { dilithium.sign(message, priv) } finally { priv.fill(0) }
    }

    fun verify(signature: ByteArray, message: ByteArray): Boolean {
        val pub = getPublicKey() ?: return false
        return dilithium.verify(signature, message, pub)
    }

    fun exportPublicKeyHex(): String? {
        val pub = getPublicKey() ?: return null
        return AddressUtils.toHex(pub)
    }

    fun exportPrivateKeyHex(): String? {
        val id = getActiveWalletId(); if (id < 0) return null
        checkWalletIntegrity(id)
        val b64 = prefs.getString("w${id}_priv", null) ?: return null
        val priv = Base64.decode(b64, Base64.NO_WRAP)
        val hex = AddressUtils.toHex(priv)
        priv.fill(0)
        return hex
    }

    fun exportBackup(): String? {
        val id = getActiveWalletId(); if (id < 0) return null
        checkWalletIntegrity(id)
        val privB64 = prefs.getString("w${id}_priv", null) ?: return null
        val pubB64 = prefs.getString("w${id}_pub", null) ?: return null
        return "$privB64:$pubB64"
    }

    fun importWallet(backup: String, name: String = ""): WalletInfo {
        require(backup.length <= 20000) { "Backup is too large" }
        val parts = backup.replace("\\s".toRegex(), "").split(":")
        require(parts.size == 2) { "Invalid key backup format" }
        val privkey = Base64.decode(parts[0], Base64.NO_WRAP)
        try {
            val pubkey = Base64.decode(parts[1], Base64.NO_WRAP)
            require(privkey.size == dilithium.getSecretKeyBytes() && pubkey.size == dilithium.getPublicKeyBytes()) { "Invalid key size" }
            val message = "qbitx-import-test".toByteArray()
            val signature = requireNotNull(dilithium.sign(message, privkey))
            require(dilithium.verify(signature, message, pubkey)) { "Key pair does not match" }
            return storeWallet(arrayOf(pubkey, privkey), null, name, true)
        } finally { privkey.fill(0) }
    }

    fun deleteWallet(id: Int) {
        validated.remove(id)
        val ids = walletIds().filter { it != id }
        val editor = prefs.edit()
            .remove("w${id}_pub")
            .remove("w${id}_priv")
            .remove("w${id}_name")
            .remove("w${id}_seed_confirmed")
            .remove("w${id}_seed")
            .remove("w${id}_derivation")
            .remove("w${id}_uncertain_raw")
            .remove("w${id}_uncertain_spent")
            .remove(pendingSpentKey(id))
            .putString("wallet_ids", if (ids.isEmpty()) "" else ids.joinToString(","))
        if (getActiveWalletId() == id) {
            editor.putInt("active_id", ids.firstOrNull() ?: -1)
        }
        editor.apply()
    }

    fun deleteAllWallets() {
        validated.clear()
        val pinHash = prefs.getString("pin_hash", null)
        val txHistory = prefs.getString("tx_history", null)
        prefs.edit().clear().apply()
        val editor = prefs.edit()
        if (pinHash != null) editor.putString("pin_hash", pinHash)
        if (txHistory != null) editor.putString("tx_history", txHistory)
        editor.apply()
    }

    // ---- PIN ----

    fun hasPin(): Boolean = prefs.contains("pin_hash")

    fun setPin(pin: String) {
        val protected=org.qbitx.wallet.crypto.PinProtection.create(pin)
        check(prefs.edit().putString("pin_hash",protected).putInt("pin_failures",0).putLong("pin_locked_until",0).commit())
    }

    fun verifyPin(pin: String): Boolean {
        val now=System.currentTimeMillis()
        if (prefs.getLong("pin_locked_until",0)>now) return false
        val stored = prefs.getString("pin_hash", null) ?: return false
        val valid=org.qbitx.wallet.crypto.PinProtection.verify(pin,stored)
        if (valid) {
            if (!stored.startsWith("v2:")) setPin(pin)
            else check(prefs.edit().putInt("pin_failures",0).putLong("pin_locked_until",0).commit())
        } else {
            val failures=(prefs.getInt("pin_failures",0)+1).coerceAtMost(5)
            check(prefs.edit().putInt("pin_failures",failures)
                .putLong("pin_locked_until",if(failures>=5) now+30000 else 0).commit())
        }
        return valid
    }

    fun removePin() {
        prefs.edit().remove("pin_hash").apply()
    }

    // ---- RPC URL ----

    fun getSavedRpcUrl(): String? = prefs.getString("rpc_url", null)

    fun saveRpcUrl(url: String) {
        prefs.edit().putString("rpc_url", url).apply()
    }

    // ---- Last Scanned Height (per wallet, for incremental block scanning) ----

    fun getLastScannedHeight(walletId: Int): Int {
        return prefs.getInt("w${walletId}_last_scan", 0)
    }

    fun setLastScannedHeight(walletId: Int, height: Int) {
        prefs.edit().putInt("w${walletId}_last_scan", height).apply()
    }

    fun getLastScanTxCount(walletId: Int): Int {
        return prefs.getInt("w${walletId}_last_scan_count", -1)
    }

    fun setLastScanTxCount(walletId: Int, count: Int) {
        prefs.edit().putInt("w${walletId}_last_scan_count", count).apply()
    }

    // ---- TX History ----

    fun addTxRecord(txid: String, toAddress: String, amount: Double, fee: String) {
        val record = TxRecord(txid, toAddress, amount, fee, System.currentTimeMillis(), getActiveWalletId(), "out", 0)
        val history = getAllTxHistory().toMutableList()
        history.add(0, record)
        check(prefs.edit().putString("tx_history", gson.toJson(history)).commit()) { "Could not persist transaction history" }
    }

    fun replaceAllTxHistory(records: List<TxRecord>) {
        prefs.edit().putString("tx_history", gson.toJson(records)).apply()
    }

    fun replaceTxHistoryForWallet(records: List<TxRecord>, walletId: Int) {
        val others = getAllTxHistory().filter { it.walletId != walletId }
        val merged = (others + records).sortedByDescending { it.timestamp }
        prefs.edit().putString("tx_history", gson.toJson(merged)).apply()
    }

    fun getAllTxHistory(): List<TxRecord> {
        val json = prefs.getString("tx_history", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<TxRecord>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { emptyList() }
    }

    fun getTxHistoryForActiveWallet(): List<TxRecord> {
        val id = getActiveWalletId()
        return getAllTxHistory().filter { it.walletId == id }
    }

    // ---- Pending-spent UTXO tracking ----
    // Outpoints (txid:vout) consumed by transactions we just broadcast and that
    // are not yet confirmed. Used to filter scanTxOutSet results so we don't
    // double-spend UTXOs already locked in mempool ("replacement fee" error).

    private fun pendingSpentKey(walletId: Int) = "w${walletId}_pending_spent"

    fun getPendingSpentOutpoints(walletId: Int = getActiveWalletId()): Set<String> {
        return prefs.getStringSet(pendingSpentKey(walletId), emptySet()) ?: emptySet()
    }

    fun addPendingSpentOutpoints(outpoints: Collection<String>, walletId: Int = getActiveWalletId()) {
        val current = prefs.getStringSet(pendingSpentKey(walletId), emptySet())?.toMutableSet()
            ?: mutableSetOf()
        current.addAll(outpoints)
        prefs.edit().putStringSet(pendingSpentKey(walletId), current).apply()
    }

    fun clearPendingSpentOutpoints(walletId: Int = getActiveWalletId()) {
        if (prefs.getStringSet("w${walletId}_uncertain_spent",emptySet())!!.isNotEmpty()) return
        prefs.edit().remove(pendingSpentKey(walletId)).apply()
    }

    fun reserveBroadcast(outpoints: Collection<String>, signedHex: String, walletId: Int = getActiveWalletId()) {
        val pending=getPendingSpentOutpoints(walletId)+outpoints
        val uncertain=prefs.getStringSet("w${walletId}_uncertain_spent",emptySet())!!.toSet()+outpoints
        check(prefs.edit().putStringSet(pendingSpentKey(walletId),pending)
            .putStringSet("w${walletId}_uncertain_spent",uncertain)
            .putString("w${walletId}_uncertain_raw",signedHex).commit()) { "Could not save payment before broadcast" }
    }
    fun acknowledgeBroadcast(outpoints: Collection<String>, walletId: Int = getActiveWalletId()) {
        val uncertain=prefs.getStringSet("w${walletId}_uncertain_spent",emptySet())!!.toSet()-outpoints.toSet()
        check(prefs.edit().putStringSet("w${walletId}_uncertain_spent",uncertain)
            .remove("w${walletId}_uncertain_raw").commit()) { "Could not save payment acknowledgement" }
    }
}
