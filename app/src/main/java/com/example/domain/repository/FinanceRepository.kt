package com.example.domain.repository

import com.example.domain.model.CalculatorSnapshot
import com.example.domain.model.FmpSearchResponse
import com.example.domain.model.TradeEntity
import com.example.domain.model.WatchlistTicker
import kotlinx.coroutines.flow.Flow

interface FinanceRepository {
    // Secure settings getters/setters
    fun getApiKey(): String
    fun getGeminiApiKey(): String
    fun saveApiKey(key: String)
    fun saveGeminiApiKey(key: String)
    fun getCurrency(): String
    fun saveCurrency(currency: String)
    fun getRiskFreeRate(): Float
    fun saveRiskFreeRate(rate: Float)
    fun getRiskPremium(): Float
    fun saveRiskPremium(premium: Float)

    // Watchlist DB Operations
    fun getWatchlist(): Flow<List<WatchlistTicker>>
    suspend fun addTicker(symbol: String, name: String)
    suspend fun removeTicker(symbol: String)
    suspend fun updateManualCostBasis(symbol: String, costBasis: Double?)
    suspend fun updateTargetPrice(symbol: String, targetPrice: Double?)
    suspend fun swapWatchlistItems(symbol1: String, symbol2: String)
    suspend fun updateWatchlistOrder(orderedSymbols: List<String>)

    // Trade Log DB Operations
    fun getAllTrades(): Flow<List<TradeEntity>>
    fun getTradesForTicker(symbol: String): Flow<List<TradeEntity>>
    suspend fun insertTrade(trade: TradeEntity)
    suspend fun deleteTrade(id: Int)

    // Calculator Snapshot DB Operations
    suspend fun getCalculatorSnapshot(symbol: String): CalculatorSnapshot?
    fun getCalculatorSnapshotFlow(symbol: String): Flow<CalculatorSnapshot?>
    fun getAllCalculatorSnapshotsFlow(): Flow<List<CalculatorSnapshot>>
    suspend fun saveCalculatorSnapshot(snapshot: CalculatorSnapshot)

    // Remote Sync / Search Operations
    suspend fun searchTickers(query: String): List<FmpSearchResponse>
    suspend fun syncTickerData(symbol: String, force: Boolean = false): CalculatorSnapshot
    suspend fun forceGeminiSync(symbol: String, force: Boolean = false): CalculatorSnapshot

    // Key Validation & Diagnostic Helpers
    suspend fun testFinnhubConnection(key: String): Result<String>
    suspend fun testGeminiConnection(key: String): Result<String>
}

