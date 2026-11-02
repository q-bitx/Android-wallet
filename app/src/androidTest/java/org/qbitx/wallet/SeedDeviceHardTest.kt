package org.qbitx.wallet

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.crypto.*
import org.qbitx.wallet.data.KeyManager
import java.lang.reflect.Proxy
import java.security.MessageDigest

fun auditPreferences(): SharedPreferences {
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    val key=MasterKey.Builder(context,"qbx-audit-test-key").setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    return EncryptedSharedPreferences.create(context,"qbx-audit-"+java.util.UUID.randomUUID(),key,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
}

@RunWith(AndroidJUnit4::class)
class SeedDeviceHardTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun digest(bytes:ByteArray)=AddressUtils.toHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    @Test fun thousandActualAndroidJniRecoveriesMatchIndependentOracle() {
        val corpus=InstrumentationRegistry.getInstrumentation().context.assets.open("public-seed-corpus.json")
            .bufferedReader().use { JsonParser.parseString(it.readText()).asJsonArray }
        val native=DilithiumNative()
        corpus.forEachIndexed { index,item ->
            val v=item.asJsonObject
            val seed=SeedWallet.keySeed(v["mnemonic"].asString)
            assertEquals(v["keySeed"].asString,AddressUtils.toHex(seed))
            val keys=native.generateKeypairFromSeed(seed)!!
            val restored=native.generateKeypairFromSeed(seed)!!
            try {
                assertArrayEquals(keys[0],restored[0]); assertArrayEquals(keys[1],restored[1])
                assertEquals(v["publicKeySha256"].asString,digest(restored[0]))
                assertEquals(v["secretKeySha256"].asString,digest(restored[1]))
                assertEquals(v["legacy"].asString,AddressUtils.pubkeyToAddress(restored[0]))
                assertEquals(v["native"].asString,AddressUtils.pubkeyToWitnessAddress(restored[0]))
                if (index%10==0) {
                    val message="Android hard test $index".toByteArray()
                    val signature=native.sign(message,restored[1])!!
                    assertTrue(native.verify(signature,message,keys[0]))
                    message[0]=(message[0].toInt() xor 1).toByte()
                    assertFalse(native.verify(signature,message,keys[0]))
                }
            } finally { seed.fill(0);keys[1].fill(0);restored[1].fill(0) }
        }
    }
    @Test fun hundredDurableEncryptedSeedFileDeleteRestoreCycles() {
        val prefs=auditPreferences()
        repeat(100) {
            val first=KeyManager(context,prefs)
            val wallet=first.createWallet("public-test-only")
            val seed=first.exportSeed()!!
            val raw=first.exportBackup()!!
            val pub=first.getPublicKey()!!
            val envelope=SeedWallet.backupEnvelope(seed)
            first.confirmSeed()
            // New manager must read encrypted storage and regenerate the same full pair.
            val reopened=KeyManager(context,prefs)
            assertEquals(raw,reopened.exportBackup())
            assertNull(reopened.pendingSeed())
            reopened.deleteWallet(wallet.id)
            val restored=KeyManager(context,prefs)
            val newWallet=restored.importRecovery(envelope)
            assertEquals(wallet.address,newWallet.address)
            assertArrayEquals(pub,restored.getPublicKey())
            assertEquals(raw,restored.exportBackup())
            val message="storage restore cycle $it".toByteArray()
            assertTrue(restored.verify(restored.sign(message)!!,message))
            restored.deleteWallet(newWallet.id)
        }
    }
    @Test fun alteredSeedVersionOrKeyFailsClosedAndSeedCanRepairPair() {
        val prefs=auditPreferences();val manager=KeyManager(context,prefs)
        val wallet=manager.createWallet(); val seed=manager.exportSeed()!!; val raw=manager.exportBackup()
        prefs.edit().putString("w${wallet.id}_derivation","qbx-pqhd-v999").commit()
        reject { KeyManager(context,prefs).sign(byteArrayOf(1)) }
        val repaired=KeyManager(context,prefs)
        repaired.restoreSeed(seed)
        assertEquals(raw,repaired.exportBackup())
        prefs.edit().putString("w${wallet.id}_seed",SeedWallet.fromEntropy(ByteArray(32) { 99 })).commit()
        reject { KeyManager(context,prefs).exportSeed() }
        KeyManager(context,prefs).restoreSeed(seed)
        val priv=android.util.Base64.decode(prefs.getString("w${wallet.id}_priv",null),android.util.Base64.NO_WRAP)
        priv[0]=(priv[0].toInt() xor 1).toByte()
        prefs.edit().putString("w${wallet.id}_priv",android.util.Base64.encodeToString(priv,android.util.Base64.NO_WRAP)).commit()
        priv.fill(0)
        reject { KeyManager(context,prefs).sign(byteArrayOf(1)) }
        KeyManager(context,prefs).restoreSeed(seed)
        assertEquals(raw,KeyManager(context,prefs).exportBackup())
    }
    @Test fun duplicateSeedImportNeverCreatesASecondBalanceOrSpendLockset() {
        val prefs=auditPreferences();val manager=KeyManager(context,prefs)
        val original=manager.createWallet();val seed=manager.exportSeed()!!
        val again=manager.importRecovery(SeedWallet.backupEnvelope(seed))
        assertEquals(original.id,again.id);assertEquals(1,manager.listWallets().size)
    }
    @Test fun diskCommitFailureNeverAcknowledgesCreatedOrRestoredWallet() {
        val prefs=auditPreferences()
        val failing=Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)) { _,method,args ->
            if(method.name=="edit") {
                val edit=prefs.edit()
                Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)) { proxy,m,a ->
                    if(m.name=="commit") false else {
                        val result=m.invoke(edit,*(a?:emptyArray()))
                        if(result is SharedPreferences.Editor) proxy else result
                    }
                }
            } else method.invoke(prefs,*(args?:emptyArray()))
        } as SharedPreferences
        reject { KeyManager(context,failing).createWallet() }
        reject { KeyManager(context,failing).restoreSeed(SeedWallet.fromEntropy(ByteArray(32))) }
        assertFalse(KeyManager(context,prefs).hasWallet())
    }
    @Test fun ambiguousBroadcastLocksSurviveRecreationAndHistoryRefresh() {
        val prefs=auditPreferences();val manager=KeyManager(context,prefs);manager.createWallet()
        val points=listOf("01".repeat(32)+":0")
        manager.reserveBroadcast(points,"public-test-transaction")
        val restarted=KeyManager(context,prefs)
        restarted.clearPendingSpentOutpoints()
        assertEquals(points.toSet(),restarted.getPendingSpentOutpoints())
        restarted.acknowledgeBroadcast(points)
        restarted.clearPendingSpentOutpoints()
        assertTrue(restarted.getPendingSpentOutpoints().isEmpty())
    }
    @Test fun persistedPinThrottlingAndSaltedHashes() {
        val prefs=auditPreferences();val manager=KeyManager(context,prefs)
        manager.setPin("582761");val first=prefs.getString("pin_hash",null)
        assertTrue(first!!.startsWith("v2:"));assertTrue(manager.verifyPin("582761"))
        manager.setPin("582761");assertNotEquals(first,prefs.getString("pin_hash",null))
        repeat(5) { assertFalse(manager.verifyPin("000000")) }
        assertFalse(KeyManager(context,prefs).verifyPin("582761"))
    }
    @Test fun malformedJniSizesAndOversizeMessagesNeverCrashOrSign() {
        val native=DilithiumNative()
        for(size in listOf(0,1,31,33,64,4096)) assertNull(native.generateKeypairFromSeed(ByteArray(size)))
        val pair=native.generateKeypairFromSeed(ByteArray(32))!!
        try {
            for(size in listOf(0,1,3308,3310,10000)) assertFalse(native.verify(ByteArray(size),byteArrayOf(),pair[0]))
            for(size in listOf(0,1,4031,4033)) assertNull(native.sign(byteArrayOf(1),ByteArray(size)))
            assertNull(native.sign(ByteArray(1048577),pair[1]))
            val empty=native.sign(byteArrayOf(),pair[1])!!
            assertTrue(native.verify(empty,byteArrayOf(),pair[0]))
        } finally { pair[1].fill(0) }
    }
    private fun reject(block:()->Unit) { try { block() } catch (_:Exception) { return };fail("Expected rejection") }
}
