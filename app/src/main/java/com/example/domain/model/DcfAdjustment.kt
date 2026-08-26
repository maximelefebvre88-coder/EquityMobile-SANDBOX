package com.example.domain.model

import androidx.compose.runtime.Immutable

/**
 * Represents the adjustments to apply to a 2-stage Discounted Cash Flow (DCF) model
 * based on the qualitative competitive moat and financial quality of a business.
 *
 * @property requiredRateOfReturnAdjustmentBps The adjustment in basis points (bps) to be added to
 * or subtracted from the base Required Rate of Return. A negative value represents a reduction
 * in the discount rate (risk premium reduction) for high-quality, high-moat businesses. A positive
 * value represents an increase in the discount rate (higher risk premium) for higher-risk businesses.
 * @property highGrowthYears The adjusted number of years for Stage 1 of the 2-stage DCF model,
 * replacing the default assumption. High-moat, high-quality businesses sustain growth longer (up to 8 years),
 * whereas low-quality businesses mean-revert rapidly (down to 2-3 years).
 * @property isLowReliability If true, indicates that the DCF calculation has a very low reliability
 * because of a weak competitive moat or unstable financial quality. The UI should display a warning
 * prompting the user to cross-check with alternative valuation metrics (e.g., earnings multiples)
 * instead of relying solely on the DCF.
 * @property compositeScore The combined weighted score (60% Moat, 40% Quality) that determined these adjustments.
 */
@Immutable
data class DcfAdjustment(
    val requiredRateOfReturnAdjustmentBps: Double,
    val highGrowthYears: Int,
    val isLowReliability: Boolean,
    val compositeScore: Double
) {
    /**
     * Helper to get the required rate of return adjustment as a percentage (e.g. -1.75 for -175 bps).
     */
    val requiredRateOfReturnAdjustmentPercent: Double
        get() = requiredRateOfReturnAdjustmentBps / 100.0
}
