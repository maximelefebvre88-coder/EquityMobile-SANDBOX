package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class PortfolioSummary(
    val totalEquity: Double = 0.0,
    val totalCashBalance: Double = 0.0,
    val buyingPower: Double = 0.0,
    val totalStockMarketValue: Double = 0.0,
    val aggregatePremiums: Double = 0.0,
    val aggregateUnrealizedPnL: Double = 0.0,
    val aggregateTotalPnL: Double = 0.0,
    val totalCspLocked: Double = 0.0,
    val manualCashDeposits: Double = 0.0,
    val implicitCashDeposits: Double = 0.0
)
