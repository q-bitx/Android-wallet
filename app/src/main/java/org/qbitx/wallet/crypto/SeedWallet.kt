package org.qbitx.wallet.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** BIP39 English, 256-bit entropy. PQ derivation is Q-BitX-specific, NOT BIP32. */
object SeedWallet {
    const val VERSION = "qbx-pqhd-v1"
    const val INDEXED_VERSION = "qbx-pqhd-v1-indexed"
    private val words: List<String> by lazy {
        SeedWallet::class.java.getResourceAsStream("/bip39-english.txt")!!
            .bufferedReader().use { it.readLines() }.also {
                require(it.size == 2048 && it == it.sorted() && it.distinct().size == 2048)
                val digest = MessageDigest.getInstance("SHA-256").digest(it.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
                require(AddressUtils.toHex(digest) == "2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda") { "Invalid BIP39 word list" }
            }
    }
    fun normalize(text: String): String {
        require(text.length <= 4096) { "Seed input is too long" }
        return Normalizer.normalize(text, Normalizer.Form.NFKD).trim()
            .split(Regex("\\s+")).joinToString(" ").lowercase(java.util.Locale.ROOT)
    }

    /** A file backup carries recovery metadata; plain 24-word import always means v1. */
    fun backupEnvelope(backup: String, index: Int = 0): String {
        require(index in 0..10000) { "Unsupported account index" }
        if (backup.contains(":")) return backup // Existing raw-key format.
        val text = validate(backup)
        return com.google.gson.JsonObject().apply {
            addProperty("format", "qbx-seed-backup")
            addProperty("version", if(index==0) 1 else 2)
            addProperty("derivation", if(index==0) VERSION else INDEXED_VERSION)
            addProperty("index", index)
            addProperty("mnemonic", text)
        }.toString()
    }

    fun readBackupEnvelope(text: String): String {
        require(text.length <= 8192) { "Backup is too large" }
        val json = com.google.gson.JsonParser.parseString(text).asJsonObject
        val index = backupIndex(text)
        require(json.get("format")?.asString == "qbx-seed-backup" &&
            ((index==0 && json.get("version")?.toString()=="1" && json.get("derivation")?.asString==VERSION) ||
             (index>0 && json.get("version")?.toString()=="2" && json.get("derivation")?.asString==INDEXED_VERSION))) { "Unsupported backup recovery format" }
        return validate(json.get("mnemonic")?.asString ?: error("Missing seed"))
    }

    fun backupIndex(text: String): Int {
        require(text.length <= 8192)
        val json=com.google.gson.JsonParser.parseString(text).asJsonObject
        val field=json.get("index")
        require(field==null || field.toString().matches(Regex("0|[1-9][0-9]{0,4}")))
        val index=field?.asInt ?: 0
        require(index in 0..10000) { "Unsupported account index" }
        return index
    }

    fun generate(): String {
        val entropy = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return try { fromEntropy(entropy) } finally { entropy.fill(0) }
    }

    fun fromEntropy(entropy: ByteArray): String {
        require(entropy.size == 32)
        val data = entropy + MessageDigest.getInstance("SHA-256").digest(entropy)[0]
        return try {
            (0 until 24).joinToString(" ") { word ->
                var index = 0
                repeat(11) { bit ->
                    val pos = word * 11 + bit
                    index = (index shl 1) or ((data[pos / 8].toInt() ushr (7 - pos % 8)) and 1)
                }
                words[index]
            }
        } finally { data.fill(0) }
    }

    fun validate(text: String): String {
        val mnemonic = normalize(text)
        val parts = mnemonic.split(' ')
        require(parts.size == 24) { "Enter exactly 24 English BIP39 words" }
        val data = ByteArray(33)
        try {
            parts.forEachIndexed { word, value ->
                val index = words.binarySearch(value)
                require(index >= 0) { "Unknown BIP39 word at position ${word + 1}" }
                repeat(11) { bit ->
                    val pos = word * 11 + bit
                    data[pos / 8] = (data[pos / 8].toInt() or
                        (((index ushr (10 - bit)) and 1) shl (7 - pos % 8))).toByte()
                }
            }
            val entropy = data.copyOf(32)
            try { require(fromEntropy(entropy) == mnemonic) { "Invalid seed checksum" } }
            finally { entropy.fill(0) }
            return mnemonic
        } finally { data.fill(0) }
    }

    fun bip39Seed(mnemonic: String, passphrase: String = ""): ByteArray {
        val spec = PBEKeySpec(normalize(mnemonic).toCharArray(),
            Normalizer.normalize("mnemonic$passphrase", Normalizer.Form.NFKD).toByteArray(Charsets.UTF_8), 2048, 512)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    /** Index 0 preserves v1 exactly; other indices use the versioned indexed extension. */
    fun keySeed(mnemonic: String, index: Int = 0): ByteArray {
        require(index in 0..10000)
        val seed = bip39Seed(validate(mnemonic))
        return try {
            val mac = Mac.getInstance("HmacSHA512")
            mac.init(SecretKeySpec(seed, "HmacSHA512"))
            val expanded = mac.doFinal("Q-BitX/PQ-HD/v1/mainnet/account/0/receive/$index/Dilithium3".toByteArray(Charsets.US_ASCII))
            try { expanded.copyOf(32) } finally { expanded.fill(0) }
        } finally { seed.fill(0) }
    }
}
