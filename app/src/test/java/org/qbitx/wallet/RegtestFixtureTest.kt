package org.qbitx.wallet

import com.google.gson.JsonParser
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.qbitx.wallet.crypto.*
import java.io.File

class RegtestFixtureTest {
    @Test fun signRealRegtestFixture() {
        val path = System.getenv("QBX_REGTEST_FIXTURE")
        assumeTrue("External localhost regtest fixture required", path != null)
        val file = File(path!!)
        val obj = JsonParser.parseString(file.readText()).asJsonObject
        val unsigned=obj["unsigned"].asString
        val pub=AddressUtils.fromHex(obj["publicKey"].asString)
        val script=obj["script"].asString
        val amount=obj["inputAmount"].asLong
        TransactionPolicy.verify(unsigned,listOf(obj["txid"].asString to obj["vout"].asInt),
            obj["recipient"].asString,obj["amount"].asLong,null,0,obj["fee"].asLong,
            listOf(amount),pub,listOf(script))
        val tx=TransactionBuilder.parseRawTx(unsigned)
        val scriptBytes=AddressUtils.fromHex(script)
        val hash=if (AddressUtils.isWitnessScriptPubKey(scriptBytes))
            TransactionBuilder.computeWitnessSighash(tx,0,scriptBytes.copyOfRange(2,22),amount)
            else TransactionBuilder.computeSighash(tx,0,scriptBytes)
        File(file.parentFile,"regtest-sighash.hex").writeText(AddressUtils.toHex(hash))
        val signatureFile=File(file.parentFile,"regtest-signature.hex")
        if (signatureFile.exists()) {
            val signature=AddressUtils.fromHex(signatureFile.readText().trim())
            val signed=TransactionBuilder.signTransaction(unsigned,mapOf(0 to script),
                { requested -> require(requested.contentEquals(hash)); signature },pub,mapOf(0 to amount))
            File(file.parentFile,"regtest-signed.hex").writeText(signed)
        }
    }
}
