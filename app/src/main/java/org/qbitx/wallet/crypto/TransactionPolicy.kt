package org.qbitx.wallet.crypto

/** Treat node-produced unsigned transactions as untrusted input. */
object TransactionPolicy {
    fun outputAddress(script: ByteArray): String = when {
        AddressUtils.isWitnessScriptPubKey(script) -> AddressUtils.hash160ToWitnessAddress(script.copyOfRange(2, 22))
        script.size == 25 && script[0] == 0x76.toByte() && script[1] == 0xa9.toByte() &&
            script[2] == 0x14.toByte() && script[23] == 0x88.toByte() && script[24] == 0xc4.toByte() ->
            AddressUtils.hash160ToAddress(script.copyOfRange(3, 23))
        else -> throw IllegalArgumentException("Unsupported output script")
    }

    fun verify(hex: String, outpoints: List<Pair<String, Int>>, recipient: String, amount: Long,
               change: String?, changeAmount: Long, fee: Long, inputAmounts: List<Long>,
               publicKey: ByteArray, inputScripts: List<String>) {
        require(hex.length <= 800_000 && hex.length % 2 == 0 && hex.all { it in "0123456789abcdefABCDEF" })
        val tx = TransactionBuilder.parseRawTx(hex)
        require(tx.version == 2 && tx.locktime == 0L) { "Unexpected transaction version/locktime" }
        require(tx.inputs.size == outpoints.size && tx.inputs.size == inputAmounts.size &&
            tx.inputs.size == inputScripts.size && outpoints.distinct().size == outpoints.size)
        require(AddressUtils.toHex(TransactionBuilder.serializeTx(tx)).equals(hex, true)) { "Non-canonical transaction" }
        val ownLegacy = AddressUtils.pubkeyToAddress(publicKey)
        val ownWitness = AddressUtils.pubkeyToWitnessAddress(publicKey)
        tx.inputs.forEachIndexed { i, input ->
            require(AddressUtils.toHex(input.txid.reversedArray()) == outpoints[i].first.lowercase() &&
                input.vout == outpoints[i].second.toLong() && input.scriptSig.isEmpty() &&
                input.sequence in listOf(0xffffffffL, 0xfffffffdL)) {
                "Unexpected input"
            }
            require(outputAddress(AddressUtils.fromHex(inputScripts[i])) in listOf(ownLegacy, ownWitness)) { "Input does not belong to this wallet" }
        }
        require(amount > 0 && changeAmount >= 0 && fee > 0 && fee <= 10_000_000L) { "Invalid amount or fee above 0.1 QBX safety limit" }
        val expected = mutableListOf(recipient to amount)
        if (change != null) {
            require(change == ownLegacy || change == ownWitness)
            expected.add(change to changeAmount)
        } else require(changeAmount == 0L)
        val actual = tx.outputs.map { outputAddress(it.scriptPubKey) to it.amount }
        require(actual.sortedBy { it.first } == expected.sortedBy { it.first }) { "RPC changed recipient, amount or change" }
        val totalInput = inputAmounts.fold(0L) { a, b -> require(b > 0); Math.addExact(a, b) }
        val totalOutput = tx.outputs.fold(0L) { a, b -> require(b.amount > 0); Math.addExact(a, b.amount) }
        require(Math.subtractExact(totalInput, totalOutput) == fee) { "Unexpected transaction fee" }
    }
}
