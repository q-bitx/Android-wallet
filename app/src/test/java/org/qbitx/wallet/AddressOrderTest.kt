package org.qbitx.wallet

import org.junit.Test
import org.junit.Assert.*
import org.qbitx.wallet.ui.sortedReceiveAddresses

class AddressOrderTest {
    @Test fun knownBalancesDescendUnknownLastAndTiesStayStable() {
        assertEquals(listOf("large", "tiny", "zero", "unknown"), sortedReceiveAddresses(
            listOf("unknown", "zero", "tiny", "large", "large"),
            mapOf("zero" to 0.0, "tiny" to 0.00000001, "large" to 23.0)))
        assertEquals(listOf("b", "a"), sortedReceiveAddresses(listOf("b", "a"), mapOf("a" to 1.0, "b" to 1.0)))
    }
}
