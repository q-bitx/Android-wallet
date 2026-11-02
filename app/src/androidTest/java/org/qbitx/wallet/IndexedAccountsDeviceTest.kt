package org.qbitx.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.qbitx.wallet.crypto.*
import org.qbitx.wallet.data.*
import org.qbitx.wallet.ui.*

@RunWith(AndroidJUnit4::class)
class IndexedAccountsDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun snapshot(manager:KeyManager)=manager.listWallets().associate { info ->
        manager.setActiveWallet(info.id);info.witnessAddress to manager.exportBackup()!!
    }
    @Test fun independentPythonCOracleMatches131ActualAndroidDerivedKeyPairs() {
        val native=DilithiumNative()
        val seed=SeedWallet.fromEntropy(ByteArray(32))
        val text=InstrumentationRegistry.getInstrumentation().context.assets.open("indexed-account-oracle.json").bufferedReader().use { it.readText() }
        val rows=com.google.gson.JsonParser.parseString(text).asJsonArray
        assertEquals(131,rows.size())
        fun digest(bytes:ByteArray)=AddressUtils.toHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
        rows.forEach { row ->
            val expected=row.asJsonObject
            val keySeed=SeedWallet.keySeed(seed,expected["index"].asInt)
            assertEquals(expected["keySeed"].asString,AddressUtils.toHex(keySeed))
            val keys=native.generateKeypairFromSeed(keySeed)!!
            keySeed.fill(0)
            try {
                assertEquals(expected["publicHash"].asString,digest(keys[0]))
                assertEquals(expected["secretHash"].asString,digest(keys[1]))
                val message="indexed account ${expected["index"].asInt}".toByteArray()
                val signature=native.sign(message,keys[1])!!
                assertTrue(native.verify(signature,message,keys[0]))
                assertFalse(native.verify(signature,message+byteArrayOf(1),keys[0]))
            } finally { keys[1].fill(0) }
        }
    }
    @Test fun newWalletHasTwoDistinctWitnessAccountsAndOneSeed() {
        val manager=KeyManager(context,auditPreferences())
        manager.createWallet()
        val accounts=manager.listWallets()
        assertEquals(2,accounts.size)
        assertEquals(2,accounts.map { it.witnessAddress }.toSet().size)
        assertTrue(accounts.all { it.witnessAddress.startsWith("dil1q") && !it.showLegacy })
        assertTrue(walletReceiveAddresses(accounts).all { it.startsWith("dil1q") })
        val seed=manager.pendingSeed()!!
        manager.confirmSeed()
        accounts.forEach { manager.setActiveWallet(it.id);assertEquals(seed,manager.exportSeed());assertNull(manager.pendingSeed()) }
        manager.createWallet()
        assertEquals(3,manager.listWallets().size)
        assertEquals(seed,manager.exportSeed());assertNull(manager.pendingSeed())
    }
    @Test fun seedRecoveryAndReAddingAccountsReproducesEveryFullKey() {
        val original=KeyManager(context,auditPreferences());original.createWallet();original.confirmSeed()
        original.createWallet();original.createWallet()
        val seed=original.exportSeed()!!
        val expected=snapshot(original)
        val restored=KeyManager(context,auditPreferences());restored.restoreSeed(seed.uppercase(java.util.Locale.ROOT))
        restored.createWallet();restored.createWallet()
        assertEquals(expected,snapshot(restored))
    }
    @Test fun singleDatExportsAllAccountsAndEveryDerivationIndex() {
        val original=KeyManager(context,auditPreferences());original.createWallet();original.confirmSeed();original.createWallet()
        val expected=snapshot(original)
        val dat=original.exportWalletDat()
        val restored=KeyManager(context,auditPreferences());restored.importWalletDat(dat)
        assertEquals(expected,snapshot(restored))
        restored.listWallets().forEach { restored.setActiveWallet(it.id);assertNotNull(restored.exportSeed());assertTrue(it.showLegacy) }
        assertEquals(6,walletReceiveAddresses(restored.listWallets()).size)
        restored.createWallet()
        assertEquals(4,restored.listWallets().size)
        dat.fill(0)
    }
    @Test fun previousSingleKeyV1StorageStillSignsAndKeepsLegacyVisible() {
        val prefs=auditPreferences()
        val seed=SeedWallet.fromEntropy(ByteArray(32))
        val native=DilithiumNative();val pair=native.generateKeypairFromSeed(SeedWallet.keySeed(seed))!!
        val raw=android.util.Base64.encodeToString(pair[1],2)+":"+android.util.Base64.encodeToString(pair[0],2)
        pair[1].fill(0)
        val original=KeyManager(context,prefs);val info=original.importWallet(raw)
        prefs.edit().putString("w${info.id}_seed",seed).putString("w${info.id}_derivation",SeedWallet.VERSION).putBoolean("w${info.id}_seed_confirmed",true).commit()
        val upgraded=KeyManager(context,prefs)
        assertEquals(raw,upgraded.exportBackup());assertTrue(upgraded.listWallets().single().showLegacy)
        val message="old v1 key after indexed accounts update".toByteArray()
        assertTrue(upgraded.verify(upgraded.sign(message)!!,message))
        upgraded.createWallet()
        assertEquals(2,upgraded.listWallets().size)
        assertEquals(raw,run { upgraded.setActiveWallet(info.id);upgraded.exportBackup() })
    }
}
