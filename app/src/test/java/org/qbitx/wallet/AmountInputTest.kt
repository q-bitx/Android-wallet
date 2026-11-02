package org.qbitx.wallet

import org.junit.Test
import org.junit.Assert.*
import org.qbitx.wallet.crypto.AmountInput

class AmountInputTest {
    @Test fun localizedDecimalDigitsKeepTheSameValue() {
        for(text in listOf("1.25","1,25","١٫٢٥","۱٫۲۵","१.२५","๑.๒๕")) assertEquals(text,1.25,AmountInput.parse(text)!!,0.0)
        assertEquals("1.25",AmountInput.normalize("١٫٢٥"))
    }
    @Test fun neverRoundsSubSatoshiOrAmbiguousInputs() {
        assertEquals(0.00000001,AmountInput.parse("0.00000001")!!,0.0)
        for(text in listOf("1e5","NaN","-1","0","0.000000001","1,234,000","1.2.3","90071992.54740991")) assertNull(text,AmountInput.parse(text))
    }
}
