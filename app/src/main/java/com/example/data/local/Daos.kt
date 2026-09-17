package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TickerDao {
    @Query("SELECT * FROM watchlist_tickers ORDER BY displayOrder ASC, symbol ASC")
    fun getAllTickers(): Flow<List<WatchlistTickerEntity>>

    @Query("SELECT * FROM watchlist_tickers ORDER BY displayOrder ASC, symbol ASC")
    suspend fun getAllTickersNonFlow(): List<WatchlistTickerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ticker: WatchlistTickerEntity)

    @Query("DELETE FROM watchlist_tickers WHERE symbol = :symbol")
    suspend fun delete(symbol: String)

    @Query("SELECT * FROM watchlist_tickers WHERE symbol = :symbol LIMIT 1")
    suspend fun getTickerBySymbol(symbol: String): WatchlistTickerEntity?

    @Query("SELECT MAX(displayOrder) FROM watchlist_tickers")
    suspend fun getMaxDisplayOrder(): Int?

    @Query("UPDATE watchlist_tickers SET livePrice = :price, companyName = :companyName, lastFetched = :timestamp, logoUrl = :logoUrl, changePercent = :changePercent WHERE symbol = :symbol")
    suspend fun updatePrice(symbol: String, price: Double, companyName: String, timestamp: Long, logoUrl: String?, changePercent: Double?)

    @Query("UPDATE watchlist_tickers SET manuallyEnteredCostBasis = :costBasis WHERE symbol = :symbol")
    suspend fun updateCostBasis(symbol: String, costBasis: Double?)

    @Query("UPDATE watchlist_tickers SET targetPrice = :targetPrice WHERE symbol = :symbol")
    suspend fun updateTargetPrice(symbol: String, targetPrice: Double?)

    @Query("UPDATE watchlist_tickers SET displayOrder = :displayOrder WHERE symbol = :symbol")
    suspend fun updateDisplayOrder(symbol: String, displayOrder: Int)

    @Query("DELETE FROM watchlist_tickers")
    suspend fun deleteAllTickers()
}

@Dao
interface TradeDao {
    @Query("SELECT * FROM trade_logs ORDER BY date DESC")
    fun getAllTrades(): Flow<List<TradeLogEntity>>

    @Query("SELECT * FROM trade_logs ORDER BY date DESC")
    suspend fun getAllTradesNonFlow(): List<TradeLogEntity>

    @Query("SELECT * FROM trade_logs WHERE ticker = :ticker ORDER BY date DESC")
    fun getTradesForTicker(ticker: String): Flow<List<TradeLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeLogEntity)

    @Query("DELETE FROM trade_logs WHERE id = :id")
    suspend fun deleteTradeById(id: Int)

    @Query("DELETE FROM trade_logs")
    suspend fun deleteAllTrades()
}

@Dao
interface CalculatorSnapshotDao {
    @Query("SELECT * FROM calculator_snapshots")
    fun getAllSnapshotsFlow(): Flow<List<CalculatorSnapshotEntity>>

    @Query("SELECT * FROM calculator_snapshots")
    suspend fun getAllSnapshotsNonFlow(): List<CalculatorSnapshotEntity>

    @Query("SELECT * FROM calculator_snapshots WHERE symbol = :symbol")
    suspend fun getSnapshot(symbol: String): CalculatorSnapshotEntity?

    @Query("SELECT * FROM calculator_snapshots WHERE symbol = :symbol")
    fun getSnapshotFlow(symbol: String): Flow<CalculatorSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: CalculatorSnapshotEntity)

    @Query("DELETE FROM calculator_snapshots WHERE symbol = :symbol")
    suspend fun deleteSnapshot(symbol: String)

    @Query("DELETE FROM calculator_snapshots")
    suspend fun deleteAllSnapshots()
}
