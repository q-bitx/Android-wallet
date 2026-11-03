package org.qbitx.wallet.network

import kotlin.math.ceil

/** The same integral rate is displayed and passed to the transaction planner. */
internal object FeeRates {
    fun fromCoinsPerKvB(value:java.math.BigDecimal):Double = value.movePointRight(5).toDouble()
    private const val MAX_RATE=1_000_000L
    fun effective(value:Double?,fallback:Long):Long {
        require(fallback in 1..MAX_RATE)
        return if(value!=null && value.isFinite() && value>0 && value<=MAX_RATE.toDouble())
            ceil(value).toLong().coerceAtLeast(1) else fallback
    }
    fun forPolicy(policy:String,low:Double,normal:Double,high:Double):Long = when(policy) {
        "low" -> effective(low,1)
        "high" -> effective(high,15)
        else -> effective(normal,5)
    }
    data class Choice(val policy:String,val policies:List<String>,val rate:Long)
    fun choices(low:Double,normal:Double,high:Double):List<Choice> =
        listOf("low" to effective(low,1),"normal" to effective(normal,5),"high" to effective(high,15))
            .groupBy { it.second }.map { (rate,entries) ->
                val policies=entries.map { it.first }
                Choice(if("normal" in policies) "normal" else policies.first(),policies,rate)
            }
}
