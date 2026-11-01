package org.qbitx.wallet

import org.junit.Assert.*
import org.junit.Test
import org.qbitx.wallet.crypto.*
import org.qbitx.wallet.network.NodeRpcClient

class WalletSecurityTest {
    private val mnemonic = List(23) { "abandon" }.joinToString(" ") + " art"
    @Test fun bip39ZeroEntropy24Words() {
        assertEquals(mnemonic, SeedWallet.fromEntropy(ByteArray(32)))
        assertEquals(mnemonic, SeedWallet.validate(mnemonic))
    }
    @Test fun bip39Official256BitVector() {
        assertEquals("bda85446c68413707090a52022edd26a1c9462295029f2e60cd7c4f2bbd3097170af7a4d73245cafa9c3cca8d561a7c3de6f5d4a10be8ed2a5e608d68f92fcc8",
            AddressUtils.toHex(SeedWallet.bip39Seed(mnemonic, "TREZOR")))
    }
    @Test fun seedRoundTripsAndRandomness() {
        val generated = (1..100).map { SeedWallet.generate() }
        assertEquals(100, generated.toSet().size)
        generated.forEach { assertEquals(it, SeedWallet.validate(it)) }
    }
    @Test fun seedRejectsBadChecksum() { rejects { SeedWallet.validate(List(24) { "abandon" }.joinToString(" ")) } }
    @Test fun seedRejectsUnknownWord() { rejects { SeedWallet.validate(mnemonic.replace("art", "qbitx")) } }
    @Test fun seedRejects12Words() { rejects { SeedWallet.validate(List(12) { "abandon" }.joinToString(" ")) } }
    @Test fun seedNormalizesWhitespace() { assertEquals(mnemonic, SeedWallet.validate("  " + mnemonic.replace(" ", "\n  ") + "  ")) }
    @Test fun derivationRestoresSameKeySeed() { assertArrayEquals(SeedWallet.keySeed(mnemonic), SeedWallet.keySeed(mnemonic)); assertEquals(32, SeedWallet.keySeed(mnemonic).size) }
    @Test fun derivationSeparatesDifferentWallets() { assertFalse(SeedWallet.keySeed(mnemonic).contentEquals(SeedWallet.keySeed(SeedWallet.fromEntropy(ByteArray(32) { 1 })))) }
    @Test fun rpcRequiresHttps() { rejects { NodeRpcClient.normalizeRpcUrl("http://example.org") } }
    @Test fun rpcRejectsCredentials() { rejects { NodeRpcClient.normalizeRpcUrl("https://user:password@example.org") } }
    @Test fun rpcRejectsFragment() { rejects { NodeRpcClient.normalizeRpcUrl("https://example.org/#secret") } }
    @Test fun rpcNormalizesSecureUrl() { assertEquals("https://example.org/rpc", NodeRpcClient.normalizeRpcUrl("example.org/rpc")) }

    private val pub = ByteArray(1952) { (it % 251).toByte() }
    private val hash get() = AddressUtils.hash160(pub)
    private val script get() = AddressUtils.witnessScriptPubKey(hash)
    private val address get() = AddressUtils.pubkeyToWitnessAddress(pub)
    private val txid = "01".repeat(32)
    private fun transaction() = TransactionBuilder.RawTransaction(2,
        mutableListOf(TransactionBuilder.TxInput(AddressUtils.fromHex(txid).reversedArray(), 0)),
        mutableListOf(TransactionBuilder.TxOutput(90000, script)), 0)
    private fun check(tx: TransactionBuilder.RawTransaction) = TransactionPolicy.verify(
        AddressUtils.toHex(TransactionBuilder.serializeTx(tx)), listOf(txid to 0), address, 90000, null, 0,
        10000, listOf(100000), pub, listOf(AddressUtils.toHex(script)))
    @Test fun transactionValidatesExactIntent() { check(transaction()) }
    @Test fun transactionRejectsAlteredAmount() { val tx=transaction(); tx.outputs[0]=TransactionBuilder.TxOutput(80000,script); rejects { check(tx) } }
    @Test fun transactionRejectsAlteredRecipient() { val tx=transaction(); tx.outputs[0]=TransactionBuilder.TxOutput(90000,AddressUtils.witnessScriptPubKey(ByteArray(20))); rejects { check(tx) } }
    @Test fun transactionRejectsExtraOutput() { val tx=transaction(); tx.outputs.add(TransactionBuilder.TxOutput(1,script)); rejects { check(tx) } }
    @Test fun transactionRejectsAlteredInput() { val tx=transaction(); tx.inputs[0]=TransactionBuilder.TxInput(ByteArray(32),0); rejects { check(tx) } }
    @Test fun transactionRejectsTimelock() { rejects { check(transaction().copy(locktime=1)) } }
    @Test fun transactionRejectsTrailingData() { rejects { TransactionBuilder.parseRawTx(AddressUtils.toHex(TransactionBuilder.serializeTx(transaction()))+"00") } }
    @Test fun transactionRejectsTruncation() { rejects { TransactionBuilder.parseRawTx("0200000001") } }
    @Test fun parserRejectsHugeCount() { rejects { TransactionBuilder.parseRawTx("02000000ffffffffffffffffff") } }
    @Test fun witnessAddressAndScriptAgree() { assertTrue(address.startsWith("dil1q")); assertEquals(address,TransactionPolicy.outputAddress(script)); assertTrue(AddressUtils.pubkeyToAddress(pub).startsWith("M")) }
    @Test fun legacyUsesCheckDilithiumSigOpcode() {
        val legacy = byteArrayOf(0x76,0xa9.toByte(),0x14) + hash + byteArrayOf(0x88.toByte(),0xc4.toByte())
        assertEquals(AddressUtils.pubkeyToAddress(pub),TransactionPolicy.outputAddress(legacy))
        legacy[24]=0xac.toByte()
        rejects { TransactionPolicy.outputAddress(legacy) }
    }
    @Test fun ripemd160KnownAnswer() { assertEquals("9c1185a5c5e9fc54612808977ee8f548b2258d31",AddressUtils.toHex(AddressUtils.ripemd160(byteArrayOf()))) }
    @Test fun plannerConservesFundsAndRespectsWeight() {
        for (count in 1..100) {
            val coins=(0 until count).map { SendPlanner.Coin(1_000_000L,it%2==0,it) }
            val plan=SendPlanner.plan(coins,500_000,5)
            val refs=plan.flatMap { it.coinRefs }; assertEquals(refs.size,refs.toSet().size)
            assertEquals(500_000L,plan.sumOf { it.sendSat })
            plan.forEach { b ->
                assertEquals(b.coinRefs.sumOf { coins[it].amountSat }, b.sendSat+b.changeSat+b.feeSat)
                val legacy=b.coinRefs.count { !coins[it].isWitness }; val witness=b.coinRefs.size-legacy
                assertTrue(TransactionBuilder.estimateTxWeight(legacy,witness,2)<=SendPlanner.MAX_BATCH_WEIGHT)
            }
        }
    }
    @Test fun plannerRejectsZeroAmount() { rejects { SendPlanner.plan(emptyList(),0,1) } }
    @Test fun plannerRejectsInsufficientFunds() { rejects { SendPlanner.plan(emptyList(),1,1) } }
    private fun rejects(action: () -> Unit) { try { action() } catch (e: Exception) { return }; fail("Expected rejection") }
}
