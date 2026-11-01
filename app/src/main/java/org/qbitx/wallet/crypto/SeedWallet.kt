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
    private val words: List<String> by lazy {
        SeedWallet::class.java.getResourceAsStream("/bip39-english.txt")!!
            .bufferedReader().use { it.readLines() }.also { require(it.size == 2048) }
    }
    fun normalize(text: String): String = Normalizer.normalize(text.trim(), Normalizer.Form.NFKD)
        .split(Regex("\\s+")).joinToString(" ")

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

    /** Hardened-only account 0 / external chain 0 / key 0. Version and path are frozen. */
    fun keySeed(mnemonic: String): ByteArray {
        val seed = bip39Seed(validate(mnemonic))
        return try {
            val mac = Mac.getInstance("HmacSHA512")
            mac.init(SecretKeySpec(seed, "HmacSHA512"))
            val expanded = mac.doFinal("Q-BitX/PQ-HD/v1/mainnet/account/0/receive/0/Dilithium3".toByteArray(Charsets.US_ASCII))
            try { expanded.copyOf(32) } finally { expanded.fill(0) }
        } finally { seed.fill(0) }
    }
}
