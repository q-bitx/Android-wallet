package org.qbitx.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.qbitx.wallet.crypto.*
import org.qbitx.wallet.data.KeyManager
import java.io.File

/** Explicitly selected only with a public localhost-regtest fixture. No network calls. */
@RunWith(AndroidJUnit4::class)
class DeviceRegtestSigningTest {
    @Test fun recoveredAndroidWalletSignsCoreRegtestTransaction() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val fixture=File(context.getExternalFilesDir(null),"audit-regtest-fixture.json")
        assumeTrue("Public localhost regtest fixture required",fixture.exists())
        val obj=JsonParser.parseString(fixture.readText()).asJsonObject
        val preferences=auditPreferences()
        val first=KeyManager(context,preferences)
        val seed=SeedWallet.fromEntropy(ByteArray(32)) // Published BIP39 test vector only.
        val wallet=first.restoreSeed(seed)
        val originalBackup=first.exportBackup()
        first.deleteWallet(wallet.id)
        val restored=KeyManager(context,preferences)
        restored.restoreSeed(SeedWallet.readBackupEnvelope(SeedWallet.backupEnvelope(seed)))
        assertEquals(originalBackup,restored.exportBackup())
        val pub=restored.getPublicKey()!!
        assertEquals(obj["publicKey"].asString,AddressUtils.toHex(pub))
        val raw=obj["unsigned"].asString;val script=obj["script"].asString;val amount=obj["inputAmount"].asLong
        TransactionPolicy.verify(raw,listOf(obj["txid"].asString to obj["vout"].asInt),obj["recipient"].asString,
            obj["amount"].asLong,null,0,obj["fee"].asLong,listOf(amount),pub,listOf(script))
        val signed=TransactionBuilder.signTransaction(raw,mapOf(0 to script), { hash ->
            val sig=restored.sign(hash)!!;assertTrue(restored.verify(sig,hash));sig
        },pub,mapOf(0 to amount))
        File(context.getExternalFilesDir(null),"audit-regtest-signed.hex").writeText(signed)
    }
}
