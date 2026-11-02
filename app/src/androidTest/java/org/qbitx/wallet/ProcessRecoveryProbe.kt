package org.qbitx.wallet

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.qbitx.wallet.data.KeyManager
import org.qbitx.wallet.crypto.SeedWallet
import java.io.File

/** Invoked explicitly twice, separated by force-stopping this isolated emulator app. */
class ProcessRecoveryProbe {
    @Test fun committedWalletSurvivesActualProcessDeath() {
        val phase=InstrumentationRegistry.getArguments().getString("qbxProcessPhase")
        assumeTrue(phase=="write" || phase=="read")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val key=MasterKey.Builder(context,"qbx-audit-test-key").setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs=EncryptedSharedPreferences.create(context,"qbx-audit-process-probe",key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        val manager=KeyManager(context,prefs)
        val mnemonic=SeedWallet.fromEntropy(ByteArray(32))
        val evidence=File(context.filesDir,"qbx-audit-process-evidence.txt")
        if(phase=="write") {
            manager.restoreSeed(mnemonic)
            evidence.writeText(android.os.Process.myPid().toString()+"\n"+manager.getAddress()+"\n"+manager.exportBackup())
        } else {
            val lines=evidence.readLines()
            assertNotEquals(lines[0].toInt(),android.os.Process.myPid())
            assertEquals(lines[1],manager.getAddress())
            assertEquals(lines[2],manager.exportBackup())
            assertEquals(mnemonic,manager.exportSeed())
            val message="actual Android process-death recovery".toByteArray()
            assertTrue(manager.verify(manager.sign(message)!!,message))
        }
    }
}
