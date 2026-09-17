package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class CalculatorSnapshot(
    val symbol: String,
    val currentPrice: Double,
    val fcfPerShare: Double,
    val revenuePerShare: Double,
    val fcfMarginPercent: Double,
    val roicPercent: Double,
    val netCashPerShare: Double,
    val sharesOutstanding: Double,
    val marketCap: Double,
    // Editable inputs
    val fcfGrowthRate: Double,
    val equityGrowthRate: Double,
    val fundamentalGrowthRate: Double,
    val historicalFcfMargin: Double,
    val riskFreeRate: Double,
    val riskPremium: Double,
    val terminalGrowthRate: Double,
    val highGrowthYears: Int,
    val historicalFcfYield: Double = 0.0,
    val lastFetched: Long,
    val ttmRevenue: Double = 0.0,
    val ttmFcf: Double = 0.0,
    val cashOnHand: Double = 0.0,
    val ltDebt: Double = 0.0,
    val interestCoverage: Double = 0.0,
    val ttmNetIncome: Double = 0.0,
    val checkedQualitativeTitles: String = ""
)
