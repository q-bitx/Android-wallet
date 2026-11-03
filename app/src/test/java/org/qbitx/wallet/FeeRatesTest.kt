package org.qbitx.wallet

import org.junit.Assert.*
import org.junit.Test
import org.qbitx.wallet.network.FeeRates
import org.qbitx.wallet.crypto.SendPlanner
import org.qbitx.wallet.crypto.TransactionBuilder
import java.math.BigDecimal

class FeeRatesTest {
    @Test fun sameNetworkEstimatesProduceOneRecommendedChoiceWithoutIncreasingFees() {
        val choice=FeeRates.choices(5.0,5.0,5.0).single()
        assertEquals("normal",choice.policy);assertEquals(listOf("low","normal","high"),choice.policies)
        assertEquals(5L,choice.rate)
        for(policy in choice.policies) assertEquals(choice.rate,FeeRates.forPolicy(policy,5.0,5.0,5.0))
    }
    @Test fun differentAndPartiallyEqualRatesRemainSelectable() {
        assertEquals(listOf(1L,5L,15L),FeeRates.choices(1.0,5.0,15.0).map { it.rate })
        val partial=FeeRates.choices(1.0,5.0,5.0)
        assertEquals(2,partial.size);assertEquals(listOf("normal","high"),partial.last().policies)
    }
    @Test fun decimalsRoundUpConsistentlyForDisplayAndPlanning() {
        val rate=FeeRates.forPolicy("normal",1.0,5.1,15.0)
        assertEquals(6L,rate)
        assertEquals(rate,FeeRates.choices(1.0,5.1,15.0).single { it.policy=="normal" }.rate)
        val plan=SendPlanner.plan(listOf(SendPlanner.Coin(100_000_000,true,0)),1_000_000,rate).single()
        assertEquals(TransactionBuilder.estimateFee(0,1,2,rate),plan.feeSat)
    }
    @Test fun exactRpcUnitConversionDoesNotTurnOneSatIntoTwo() {
        for((coins,sats) in listOf("0.00001" to 1L,"0.00005" to 5L,"0.00015" to 15L)) {
            val converted=FeeRates.fromCoinsPerKvB(BigDecimal(coins))
            assertEquals(sats.toDouble(),converted,0.0);assertEquals(sats,FeeRates.effective(converted,5))
        }
    }
    @Test fun invalidRatesRetainSafeDefaultsAndDifferentChoicesReallyChangeTheFee() {
        for(value in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0,0.0,1e100)) assertEquals(5L,FeeRates.effective(value,5))
        val fees=FeeRates.choices(1.0,5.0,15.0).map { choice ->
            SendPlanner.plan(listOf(SendPlanner.Coin(100_000_000,true,0)),1_000_000,choice.rate).single().feeSat
        }
        assertTrue(fees[0]<fees[1] && fees[1]<fees[2])
    }
}
