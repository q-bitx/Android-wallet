package org.qbitx.wallet

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File

/** Reflective public API only: runs against both the shipped 1.6.0 APK and 1.6.1. */
class UpgradeRecoveryProbe {
    @Test fun oldApkWalletSurvivesActualApplicationUpgrade() {
        val phase=InstrumentationRegistry.getArguments().getString("qbxUpgradePhase")
        assumeTrue(phase=="write" || phase=="read")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val version=context.packageManager.getPackageInfo(context.packageName,0).longVersionCode
        assertEquals(if(phase=="write") 26L else 27L,version)
        println("Verified APK version code $version for upgrade phase $phase")
        val type=Class.forName("org.qbitx.wallet.data.KeyManager")
        val oldConstructor=type.constructors.firstOrNull { it.parameterTypes.size==1 }
        val manager=if(oldConstructor!=null) oldConstructor.newInstance(context)
            else type.getConstructor(android.content.Context::class.java,android.content.SharedPreferences::class.java).newInstance(context,null)
        fun call(name:String)=type.getMethod(name).invoke(manager)
        val mnemonic=List(23) { "abandon" }.joinToString(" ")+" art"
        val file=File(context.filesDir,"qbx-audit-upgrade-evidence.txt")
        if(phase=="write") {
            type.getMethod("restoreSeed",String::class.java,String::class.java).invoke(manager,mnemonic,"Public upgrade test only")
            file.writeText(call("getAddress").toString()+"\n"+call("exportBackup").toString())
        } else {
            val lines=file.readLines()
            assertEquals(lines[0],call("getAddress"));assertEquals(lines[1],call("exportBackup"))
            assertEquals(mnemonic,call("exportSeed"))
            val msg="Actual APK 1.6.0 -> 1.6.1 upgrade".toByteArray()
            val signature=type.getMethod("sign",ByteArray::class.java).invoke(manager,msg) as ByteArray
            assertTrue(type.getMethod("verify",ByteArray::class.java,ByteArray::class.java).invoke(manager,signature,msg) as Boolean)
        }
    }
}
