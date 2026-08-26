package com.example.domain.usecase

class CalculateBuffettShortcutUseCase {
    operator fun invoke(
        fcfPerShare: Double,
        fcfGrowthRate: Double,
        riskFreeRate: Double,
        riskPremium: Double,
        fundamentalGrowthRate: Double,
        netCashPerShare: Double
    ): Double {
        val requiredReturn = riskFreeRate + riskPremium
        val g = fcfGrowthRate / 100.0
        val r = requiredReturn / 100.0
        val gFond = fundamentalGrowthRate / 100.0

        val denominator = r - gFond
        if (denominator <= 0.0) return 0.0

        // Formula: (FCF per share * FCF growth multiplier) / (Required return - Fond. growth rate) + net cash
        val growthMultiplier = 1.0 + g
        return ((fcfPerShare * growthMultiplier) / denominator) + netCashPerShare
    }
}
