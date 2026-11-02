package org.qbitx.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.crypto.*
import org.qbitx.wallet.data.KeyManager

@RunWith(AndroidJUnit4::class)
class NativeRecoveryTest {
    @Test fun nativeSeedRestoreAndSign() {
        val crypto = DilithiumNative()
        val mnemonic = SeedWallet.fromEntropy(ByteArray(32))
        val seed = SeedWallet.keySeed(mnemonic)
        val first = crypto.generateKeypairFromSeed(seed)!!
        val restored = crypto.generateKeypairFromSeed(seed)!!
        try {
            assertArrayEquals(first[0], restored[0]); assertArrayEquals(first[1], restored[1])
            val msg = ByteArray(32) { it.toByte() }
            val sig = crypto.sign(msg, restored[1])!!
            assertTrue(crypto.verify(sig, msg, first[0]))
            msg[0] = 99
            assertFalse(crypto.verify(sig, msg, first[0]))
            assertEquals(1952, first[0].size); assertEquals(4032, first[1].size)
            assertTrue(AddressUtils.pubkeyToWitnessAddress(first[0]).startsWith("dil1q"))
        } finally { seed.fill(0); first[1].fill(0); restored[1].fill(0) }
    }

    @Test fun encryptedWalletSurvivesManagerRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = auditPreferences()
        val first = KeyManager(context,prefs)
        val created = first.createWallet("test-only")
        val seed = first.exportSeed()!!
        val restarted = KeyManager(context,prefs)
        assertEquals(created.address, restarted.getAddress())
        assertEquals(seed, restarted.exportSeed())
        restarted.deleteWallet(created.id)
        val recovered = restarted.restoreSeed(seed,"restored-test")
        assertEquals(created.address,recovered.address)
        val message = "offline recovery test".toByteArray()
        assertTrue(restarted.verify(restarted.sign(message)!!,message))
        restarted.deleteWallet(recovered.id)
    }
}
