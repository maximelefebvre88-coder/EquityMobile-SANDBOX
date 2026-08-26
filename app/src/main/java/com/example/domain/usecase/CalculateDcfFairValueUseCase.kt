package com.example.domain.usecase

import com.example.domain.model.DcfAdjustment

class CalculateDcfFairValueUseCase {
    operator fun invoke(
        fcfPerShare: Double,
        growthRate: Double,
        requiredReturn: Double,
        terminalGrowth: Double,
        highGrowthYears: Int,
        netCashPerShare: Double
    ): Double {
        val g = growthRate / 100.0
        val r = requiredReturn / 100.0
        val gT = terminalGrowth / 100.0

        if (r <= gT) return 0.0

        var presentValueSum = 0.0
        var fcf = fcfPerShare

        for (t in 1..highGrowthYears) {
            fcf *= (1.0 + g)
            val discountFactor = Math.pow(1.0 + r, t.toDouble())
            presentValueSum += fcf / discountFactor
        }

        val terminalValue = (fcf * (1.0 + gT)) / (r - gT)
        val discountedTerminalValue = terminalValue / Math.pow(1.0 + r, highGrowthYears.toDouble())

        return presentValueSum + discountedTerminalValue + netCashPerShare
    }

    /**
     * Calculates the fair value with dynamic Moat-Quality adjustments applied.
     *
     * @param fcfPerShare Free cash flow per share
     * @param growthRate Growth rate of free cash flow in percent (e.g. 15.0 for 15%)
     * @param baseRequiredReturn The unadjusted Required Rate of Return in percent (e.g. 10.0 for 10%)
     * @param terminalGrowth Terminal growth rate in percent (e.g. 3.0 for 3%)
     * @param netCashPerShare Net cash per share
     * @param moatScore Competitiveness moat score (0-100)
     * @param qualityScore Financial quality score (0-100)
     * @return A pair of the final fair value and the adjustments applied.
     */
    fun calculateWithMoatQuality(
        fcfPerShare: Double,
        growthRate: Double,
        baseRequiredReturn: Double,
        terminalGrowth: Double,
        netCashPerShare: Double,
        moatScore: Double,
        qualityScore: Double
    ): Pair<Double, DcfAdjustment> {
        val composite = MoatQualityAdjuster.calculateCompositeScore(moatScore, qualityScore)
        val adjustment = MoatQualityAdjuster.adjust(composite)
        
        // Note: requiredRateOfReturnAdjustmentPercent is in percent (e.g. -1.75 for -175 bps)
        val adjustedRequiredReturn = baseRequiredReturn + adjustment.requiredRateOfReturnAdjustmentPercent
        
        val fairValue = invoke(
            fcfPerShare = fcfPerShare,
            growthRate = growthRate,
            requiredReturn = adjustedRequiredReturn,
            terminalGrowth = terminalGrowth,
            highGrowthYears = adjustment.highGrowthYears,
            netCashPerShare = netCashPerShare
        )
        
        return Pair(fairValue, adjustment)
    }
}
