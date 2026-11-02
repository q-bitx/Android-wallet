package org.qbitx.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.qbitx.wallet.crypto.*
import org.qbitx.wallet.data.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class WalletDatTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun rejects(action: () -> Unit) { try { action() } catch(e: Exception) { return }; fail("Expected rejection") }
    @Test fun seedAndKeysSurviveActualSqliteWalletDatRoundTrip() {
        val manager=KeyManager(context,auditPreferences())
        manager.deleteAllWallets()
        val seed=SeedWallet.fromEntropy(ByteArray(32)) // public test fixture only
        manager.restoreSeed(seed)
        val backup=manager.exportBackup()
        val address=manager.getAddress()
        val dat=manager.exportWalletDat()
        assertEquals("SQLite format 3\u0000",String(dat.copyOfRange(0,16)))
        manager.deleteAllWallets()
        manager.importWalletDat(dat)
        assertEquals(seed,manager.exportSeed())
        assertEquals(backup,manager.exportBackup())
        assertEquals(address,manager.getAddress())
        // Only this known public test fixture is exported for independent Core tests.
        File(context.getExternalFilesDir(null),"public-fixture-wallet.dat").writeBytes(dat)
        dat.fill(0)
        manager.deleteAllWallets()
    }
    @Test fun allCoreGeneratedKeysImportLosslessly() {
        val folder=context.getExternalFilesDir(null)!!
        val file=File(folder,"core-rewritten-public-fixture.dat")
        org.junit.Assume.assumeTrue("Independent Core fixture supplied by integration runner",file.exists())
        val expected=com.google.gson.JsonParser.parseString(File(folder,"core-fixture-expected.json").readText()).asJsonArray
        val manager=KeyManager(context,auditPreferences())
        manager.importWalletDat(file.readBytes())
        assertEquals(expected.size(),manager.listWallets().size)
        val actual=manager.listWallets().map { wallet ->
            manager.setActiveWallet(wallet.id)
            val pair=manager.exportBackup()!!.split(":")
            val sk=android.util.Base64.decode(pair[0],android.util.Base64.NO_WRAP)
            val pk=android.util.Base64.decode(pair[1],android.util.Base64.NO_WRAP)
            try { AddressUtils.toHex(java.security.MessageDigest.getInstance("SHA-256").digest(pk)) to AddressUtils.toHex(java.security.MessageDigest.getInstance("SHA-256").digest(sk)) }
            finally { sk.fill(0) }
        }.toSet()
        val wanted=expected.map { it.asJsonObject["public_sha256"].asString to it.asJsonObject["secret_sha256"].asString }.toSet()
        assertEquals(wanted,actual)
        manager.importWalletDat(file.readBytes())
        assertEquals(expected.size(),manager.listWallets().size)
        manager.deleteAllWallets()
    }
    @Test fun corruptWrongNetworkAndEncryptedFilesDoNotReplaceWallet() {
        val manager=KeyManager(context,auditPreferences())
        manager.deleteAllWallets(); manager.restoreSeed(SeedWallet.fromEntropy(ByteArray(32)))
        val expected=manager.exportBackup()
        val dat=manager.exportWalletDat()
        rejects { manager.importWalletDat(dat.copyOf(50)) }
        rejects { manager.importWalletDat(ByteArray(512)) }
        val file=File(context.cacheDir,"mutation-wallet.dat")
        try {
            file.writeBytes(dat)
            android.database.sqlite.SQLiteDatabase.openDatabase(file.path,null,0).use { db -> db.execSQL("PRAGMA application_id=1") }
            rejects { manager.importWalletDat(file.readBytes()) }
            file.writeBytes(dat)
            android.database.sqlite.SQLiteDatabase.openDatabase(file.path,null,0).use { db ->
                db.execSQL("INSERT INTO main(key,value) VALUES(?,?)",arrayOf(byteArrayOf(4)+"mkey".toByteArray(),byteArrayOf(0)))
            }
            rejects { manager.importWalletDat(file.readBytes()) }
            file.writeBytes(dat)
            android.database.sqlite.SQLiteDatabase.openDatabase(file.path,null,0).use { db ->
                db.execSQL("UPDATE main SET value=zeroblob(length(value)) WHERE hex(key) LIKE '0564696C736B%'")
            }
            rejects { manager.importWalletDat(file.readBytes()) }
            assertEquals(expected,manager.exportBackup())
        } finally { file.delete(); dat.fill(0); manager.deleteAllWallets() }
    }
}
