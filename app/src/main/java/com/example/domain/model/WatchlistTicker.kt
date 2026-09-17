package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class WatchlistTicker(
    val symbol: String,
    val companyName: String,
    val livePrice: Double = 0.0,
    val lastFetched: Long = 0L,
    val manuallyEnteredCostBasis: Double? = null,
    val targetPrice: Double? = null,
    val targetYield: Double? = null,
    val logoUrl: String? = null,
    val displayOrder: Int = 0,
    val changePercent: Double? = null
)

