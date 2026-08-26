package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watchlist_tickers",
    indices = [
        Index(value = ["displayOrder"])
    ]
)
data class WatchlistTickerEntity(
    @PrimaryKey val symbol: String,
    val companyName: String,
    val livePrice: Double = 0.0,
    val lastFetched: Long = 0L,
    val manuallyEnteredCostBasis: Double? = null,
    val targetPrice: Double? = null,
    val logoUrl: String? = null,
    val displayOrder: Int = 0,
    val changePercent: Double? = null
)

@Entity(
    tableName = "trade_logs",
    indices = [
        Index(value = ["ticker"]),
        Index(value = ["date"]),
        Index(value = ["ticker", "date"])
    ]
)
data class TradeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
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

@Entity(
    tableName = "calculator_snapshots",
    indices = [
        Index(value = ["lastFetched"])
    ]
)
data class CalculatorSnapshotEntity(
    @PrimaryKey val symbol: String,
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
    val ttmNetIncome: Double = 0.0,
    val checkedQualitativeTitles: String = ""
)
