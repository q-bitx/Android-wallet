package org.qbitx.wallet

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import org.qbitx.wallet.crypto.*

class SeedHardTest {
    private fun resource(name: String) = javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readText() }
    @Test fun thousandIndependentPythonReferenceSeedsMatchExactly() {
        val corpus=JsonParser.parseString(resource("public-seed-corpus.json")).asJsonArray
        assertEquals(1000,corpus.size())
        corpus.forEach { item ->
            val row=item.asJsonObject
            val text=SeedWallet.fromEntropy(AddressUtils.fromHex(row["entropy"].asString))
            assertEquals(row["mnemonic"].asString,text)
            assertEquals(row["bip39Seed"].asString,AddressUtils.toHex(SeedWallet.bip39Seed(text)))
            assertEquals(row["keySeed"].asString,AddressUtils.toHex(SeedWallet.keySeed(text)))
            assertEquals(text,SeedWallet.validate("\n  "+text.replace(" ","\t\u00a0")+"\n"))
            assertEquals(text,SeedWallet.readBackupEnvelope(SeedWallet.backupEnvelope(text)))
        }
    }
    @Test fun allOfficialEnglish256BitBip39VectorsIncludingPassphrase() {
        val vectors=JsonParser.parseString(resource("bip39-official-english.json")).asJsonArray
        assertTrue(vectors.size()>=8)
        vectors.forEach { v ->
            val row=v.asJsonArray
            assertEquals(row[1].asString,SeedWallet.fromEntropy(AddressUtils.fromHex(row[0].asString)))
            assertEquals(row[2].asString,AddressUtils.toHex(SeedWallet.bip39Seed(row[1].asString,"TREZOR")))
        }
    }
    @Test fun mutationsAreRejectedOrSelectADifferentSeedNeverSilentlyTheOriginal() {
        val corpus=JsonParser.parseString(resource("public-seed-corpus.json")).asJsonArray
        var rejected=0; var validButDifferent=0
        corpus.forEach { entry ->
            val original=entry.asJsonObject["mnemonic"].asString
            val words=original.split(' ').toMutableList()
            words[0]=if(words[0]=="abandon") "zoo" else "abandon"
            val changed=words.joinToString(" ")
            try {
                val validated=SeedWallet.validate(changed)
                assertFalse(SeedWallet.keySeed(original).contentEquals(SeedWallet.keySeed(validated)))
                validButDifferent++
            } catch (e: IllegalArgumentException) { rejected++ }
        }
        assertEquals(1000,rejected+validButDifferent)
        assertTrue(rejected>950)
    }
    @Test fun refusesUnknownVersionsMissingFieldsAndExcessiveInput() {
        val text=SeedWallet.fromEntropy(ByteArray(32))
        val envelope=SeedWallet.backupEnvelope(text)
        reject { SeedWallet.readBackupEnvelope(envelope.replace("qbx-pqhd-v1","qbx-pqhd-v2")) }
        reject { SeedWallet.readBackupEnvelope(envelope.replace("\"version\":1","\"version\":2")) }
        reject { SeedWallet.readBackupEnvelope("{}") }
        reject { SeedWallet.validate("a".repeat(1_000_000)) }
        reject { SeedWallet.validate(text.replace(" ","\u200b")) }
    }
    private fun reject(block:()->Unit) { try { block() } catch (_:Exception) { return }; fail("Expected rejection") }
}
