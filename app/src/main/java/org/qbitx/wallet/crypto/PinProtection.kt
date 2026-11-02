package org.qbitx.wallet.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinProtection {
    fun create(pin: String): String {
        require(pin.length in 4..12 && pin.all { it in '0'..'9' })
        val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "v2:${AddressUtils.toHex(salt)}:${AddressUtils.toHex(derive(pin,salt))}"
    }
    fun verify(pin: String, stored: String): Boolean {
        if (pin.length !in 4..12 || pin.any { it !in '0'..'9' }) return false
        return try {
            if (!stored.startsWith("v2:")) {
                MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest("qbitx-pin:$pin".toByteArray()),AddressUtils.fromHex(stored))
            } else {
                val parts=stored.split(':'); require(parts.size==3)
                val salt=AddressUtils.fromHex(parts[1]); val hash=AddressUtils.fromHex(parts[2])
                require(salt.size==16 && hash.size==32)
                MessageDigest.isEqual(hash,derive(pin,salt))
            }
        } catch (_:Exception) { false }
    }
    private fun derive(pin: String,salt: ByteArray): ByteArray {
        val spec=PBEKeySpec(pin.toCharArray(),salt,120000,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
