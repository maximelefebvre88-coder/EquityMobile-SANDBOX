package com.example

import com.example.domain.usecase.MoatQualityAdjuster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests to sanity check the [MoatQualityAdjuster] interpolation logic
 * and financial reasoning across various business scoring profiles.
 */
class MoatQualityAdjusterTest {

    @Test
    fun testCompositeScoreWeighting() {
        // Moat weighted 55%, Quality weighted 45%
        // Moat = 100, Quality = 50 -> (100 * 0.55) + (50 * 0.45) = 55 + 22.5 = 77.5
        val composite = MoatQualityAdjuster.calculateCompositeScore(100.0, 50.0)
        assertEquals(77.5, composite, 0.001)
    }

    @Test
    fun testPristineScore95() {
        // Score 95 corresponds to the midpoint of the 90-100 band.
        // Expected required rate of return adjustment: -175 bps
        // Expected high growth years: 8 years
        // Low reliability flag: false
        val adjustment = MoatQualityAdjuster.adjust(95.0)

        assertEquals(-175.0, adjustment.requiredRateOfReturnAdjustmentBps, 0.001)
        assertEquals(8, adjustment.highGrowthYears)
        assertFalse(adjustment.isLowReliability)
    }

    @Test
    fun testHighQualityScore80() {
        // Score 80 lies in the 75-89 band (midpoint 82 where bps adjustment is -87.5).
        // It's interpolated between 62.0 (0 bps) and 82.0 (-87.5 bps).
        // Expected required rate of return adjustment should be close to -78.75 bps
        // Expected high growth years should interpolate to 6 years
        val adjustment = MoatQualityAdjuster.adjust(80.0)

        // -87.5 + (80.0 - 82.0) * (0.0 - (-87.5)) / (62.0 - 82.0)
        // = -87.5 + (-2.0) * (87.5) / (-20.0) = -87.5 + 8.75 = -78.75 bps
        assertEquals(-78.75, adjustment.requiredRateOfReturnAdjustmentBps, 0.001)
        assertEquals(6, adjustment.highGrowthYears)
        assertFalse(adjustment.isLowReliability)
    }

    @Test
    fun testAverageScore60() {
        // Score 60 lies in the 50-74 band (midpoint 62 where bps adjustment is 0.0).
        // It is interpolated between 39.5 (+125.0 bps) and 62.0 (0.0 bps).
        // Expected required rate of return adjustment should be slightly positive (+11.11 bps).
        // Expected high growth years: 5 years (both bounding midpoints have 5 years)
        val adjustment = MoatQualityAdjuster.adjust(60.0)

        assertEquals(11.11, adjustment.requiredRateOfReturnAdjustmentBps, 0.01)
        assertEquals(5, adjustment.highGrowthYears)
        assertFalse(adjustment.isLowReliability)
    }

    @Test
    fun testSpeculativeScore40() {
        // Score 40 lies in the 30-49 band (midpoint 39.5 where bps adjustment is +125.0).
        // It's interpolated between 39.5 (+125.0 bps) and 62.0 (0.0 bps).
        // Expected required rate of return adjustment should be slightly below +125.0 (+122.22 bps).
        // Expected high growth years: 5 years
        val adjustment = MoatQualityAdjuster.adjust(40.0)

        assertEquals(122.22, adjustment.requiredRateOfReturnAdjustmentBps, 0.01)
        assertEquals(5, adjustment.highGrowthYears)
        assertFalse(adjustment.isLowReliability)
    }

    @Test
    fun testWeakScore15() {
        // Score 15 lies in the 0-29 band (midpoint 14.5 where bps adjustment is +225.0).
        // It is interpolated between 14.5 (+225.0 bps, 2.5 years) and 39.5 (+125.0 bps, 5.0 years).
        // Expected required rate of return adjustment should be slightly below +225.0 (+223.0 bps).
        // Expected high growth years: 3 years
        // Low reliability flag: true (since < 30.0)
        val adjustment = MoatQualityAdjuster.adjust(15.0)

        assertEquals(223.0, adjustment.requiredRateOfReturnAdjustmentBps, 0.01)
        assertEquals(3, adjustment.highGrowthYears)
        assertTrue(adjustment.isLowReliability)
    }
}
