package org.qbitx.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.network.*

@RunWith(AndroidJUnit4::class)
class LiveFeeRpcDeviceTest {
    @Test fun equalLiveEstimatesProduceOneChoiceWithoutArtificialPriceDifferences()=runBlocking {
        val expected=InstrumentationRegistry.getArguments().getString("live_fee_expected")?.toLongOrNull()
        assumeTrue("Explicit live fee check required",expected!=null)
        val (low,normal,high)=NodeRpcClient().fetchFeeRates()
        assertEquals(expected!!.toDouble(),low,0.0)
        assertEquals(expected.toDouble(),normal,0.0)
        assertEquals(expected.toDouble(),high,0.0)
        val choice=FeeRates.choices(low,normal,high).single()
        assertEquals(expected,choice.rate)
    }
}
