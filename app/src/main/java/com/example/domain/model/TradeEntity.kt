package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class TradeEntity(
    val id: Int = 0,
    val ticker: String,
    val tradeType: String, // "Sell CSP", "Buy to Close", "Assignment", "Sell CC", "Called Away"
    val date: Long,
    val contracts: Int = 1,
    val strikePrice: Double = 0.0,
    val premiumPerShare: Double = 0.0,
    val expiryDate: Long? = null,
    val fees: Double = 0.0,
    val netCreditDebit: Double = 0.0,
    val annualizedReturn: Double = 0.0,
    val fcfYield: Double = 0.0,
    val isClosed: Boolean = false,
    val closePremium: Double = 0.0,
    val closeFees: Double = 0.0,
    val closeDate: Long? = null,
    val manualOutcome: String = ""
)
