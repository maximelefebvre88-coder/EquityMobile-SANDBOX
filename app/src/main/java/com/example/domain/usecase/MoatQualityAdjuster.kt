package com.example.domain.usecase

import com.example.domain.model.DcfAdjustment
import kotlin.math.roundToInt

/**
 * MoatQualityAdjuster implements the financial logic to dynamically adjust key inputs of a
 * 2-stage Discounted Cash Flow (DCF) model based on qualitative and quantitative business health.
 *
 * It bridges qualitative durability (Competitive Moat) and quantitative financial quality (Profit Quality)
 * to refine the valuation model, making it more robust against arbitrary default inputs.
 */
object MoatQualityAdjuster {

    val qualitativeCriteriaPoints = mapOf(
        "Smart Capital Allocation" to 4,
        "Visionary Leadership" to 3,
        "Pricing Power" to 10,
        "Toll Bridge Economics" to 8,
        "Recurring Revenue" to 6,
        "Mission Critical, Small Ticket" to 5,
        "Unrealized Pricing Power" to 4,
        "Economies of Scale" to 2,
        "High ROIC / Capital-Light Model" to 2,
        "Monopoly Characteristic" to 12,
        "Network Effect" to 10,
        "High Switching Cost" to 9,
        "Entrenched Assets" to 7,
        "High Barrier to Entry" to 7,
        "Intangible Assets" to 6,
        "Clear Industry Secular Tailwind" to 4,
        "Regulatory/Licensing Protection" to 1
    )

    fun calculateMoatScore(checkedTitles: String): Double {
        if (checkedTitles.isEmpty()) return 0.0
        val delimiter = if (checkedTitles.contains(";")) ";" else ","
        val titles = checkedTitles.split(delimiter).map { it.trim() }
        return titles.sumOf { qualitativeCriteriaPoints[it] ?: 0 }.toDouble()
    }

    /**
     * Calculates the composite business score by combining the Moat Score and Quality Score
     * with a 55/45 weighting.
     *
     * Moat Score is weighted higher (55%) because a strong competitive moat is the primary driver
     * of long-term cash flow durability, pricing power, and terminal value protection.
     * Quality Score is weighted slightly lower (45%) because historical near-term financial metrics (ROIC, margins)
     * reflect near-term performance but are subject to disruption without a solid moat.
     *
     * @param moatScore A score from 0 to 100 measuring competitive advantage durability.
     * @param qualityScore A score from 0 to 100 measuring financial quality.
     * @return A weighted composite score from 0.0 to 100.0.
     */
    fun calculateCompositeScore(moatScore: Double, qualityScore: Double): Double {
        val clampedMoat = moatScore.coerceIn(0.0, 100.0)
        val clampedQuality = qualityScore.coerceIn(0.0, 100.0)
        return (clampedMoat * 0.55) + (clampedQuality * 0.45)
    }

    /**
     * Determines the appropriate DCF adjustments based on the calculated composite score
     * using smooth linear interpolation between band midpoints to prevent jarring valuation jumps.
     *
     * Financial rationale for adjustments:
     * 1. Required Rate of Return: Higher composite scores signal predictable, low-risk earnings,
     *    justifying a lower risk premium (negative basis points adjustment). Lower scores indicate structural
     *    vulnerability, demanding a higher risk premium (positive basis points adjustment).
     * 2. High Growth Years: Businesses with durable competitive moats can fend off competition and sustain
     *    above-average growth rates for longer (up to 8 years). Commodity-like or low-quality businesses
     *    face rapid mean reversion, justifying only 2 to 3 years of high growth.
     * 3. Reliability: Highly speculative or low-moat companies have extremely sensitive and unpredictable
     *    DCF valuations. Under a composite score of 30, the model recommends flagging the DCF value
     *    as low reliability, urging a transition to earnings-multiple or asset-based checks.
     *
     * @param compositeScore The combined business score (0.0 to 100.0).
     * @return A [DcfAdjustment] containing the interpolated adjustments.
     */
    fun adjust(compositeScore: Double): DcfAdjustment {
        val score = compositeScore.coerceIn(0.0, 100.0)

        // Interpolation anchor points (Score, RequiredRateOfReturnAdjustmentBps, HighGrowthYears)
        // 1. Boundary: Score = 0.0 (Worst-case profile) -> +250bps, 2.0 years
        // 2. Midpoint of 0-29 band: Score = 14.5 -> +225bps, 2.5 years
        // 3. Midpoint of 30-49 band: Score = 39.5 -> +125bps, 5.0 years
        // 4. Midpoint of 50-74 band: Score = 62.0 -> 0bps, 5.0 years
        // 5. Midpoint of 75-89 band: Score = 82.0 -> -87.5bps, 6.5 years
        // 6. Midpoint of 90-100 band: Score = 95.0 -> -175bps, 8.0 years
        // 7. Boundary: Score = 100.0 (Pristine profile) -> -200bps, 8.0 years
        val anchors = listOf(
            Anchor(0.0, 250.0, 2.0),
            Anchor(14.5, 225.0, 2.5),
            Anchor(39.5, 125.0, 5.0),
            Anchor(62.0, 0.0, 5.0),
            Anchor(82.0, -87.5, 6.5),
            Anchor(95.0, -175.0, 8.0),
            Anchor(100.0, -200.0, 8.0)
        )

        // Find the bounding anchors for the current score
        var lower = anchors.first()
        var upper = anchors.last()

        for (i in 0 until anchors.size - 1) {
            val current = anchors[i]
            val next = anchors[i + 1]
            if (score >= current.score && score <= next.score) {
                lower = current
                upper = next
                break
            }
        }

        // Perform linear interpolation
        val range = upper.score - lower.score
        val (interpolatedBps, interpolatedYears) = if (range == 0.0) {
            lower.bpsAdjustment to lower.years
        } else {
            val fraction = (score - lower.score) / range
            val bps = lower.bpsAdjustment + fraction * (upper.bpsAdjustment - lower.bpsAdjustment)
            val yrs = lower.years + fraction * (upper.years - lower.years)
            bps to yrs
        }

        val highGrowthYearsInt = interpolatedYears.roundToInt().coerceIn(2, 8)
        val isLowReliability = score < 30.0

        return DcfAdjustment(
            requiredRateOfReturnAdjustmentBps = interpolatedBps,
            highGrowthYears = highGrowthYearsInt,
            isLowReliability = isLowReliability,
            compositeScore = score
        )
    }

    private data class Anchor(
        val score: Double,
        val bpsAdjustment: Double,
        val years: Double
    )
}
