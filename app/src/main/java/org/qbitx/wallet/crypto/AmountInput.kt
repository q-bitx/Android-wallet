package org.qbitx.wallet.crypto

import java.math.BigDecimal

/** Decimal keyboard input: normalize digits and decimal separators, never round money. */
object AmountInput {
    fun normalize(text: String): String = text.map { c ->
        val digit=Character.digit(c,10)
        when {
            digit>=0 -> ('0'.code+digit).toChar()
            c==',' || c=='\u066b' -> '.'
            else -> c
        }
    }.joinToString("")
    fun parse(text: String): Double? {
        val canonical=normalize(text).trim()
        if(!canonical.matches(Regex("[0-9]+(?:\\.[0-9]{0,8})?"))) return null
        return try {
            val decimal=BigDecimal(canonical)
            if(decimal.signum()<=0) return null
            decimal.movePointRight(8).longValueExact()
            val amount=decimal.toDouble()
            if(!amount.isFinite() || BigDecimal.valueOf(amount).compareTo(decimal)!=0) null else amount
        } catch(_: ArithmeticException) { null }
    }
}
