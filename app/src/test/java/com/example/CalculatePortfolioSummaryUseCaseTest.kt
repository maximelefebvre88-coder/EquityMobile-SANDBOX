package com.example

import com.example.domain.model.TradeEntity
import com.example.domain.model.WatchlistTicker
import com.example.domain.usecase.CalculatePortfolioSummaryUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatePortfolioSummaryUseCaseTest {

    private val useCase = CalculatePortfolioSummaryUseCase()

    @Test
    fun testUnfundedSharePurchaseAssumesMoneyAddedBeforehand() {
        val now = System.currentTimeMillis()

        // User starts with 0 cash deposits, buys 10 AAPL @ $150 ($1,500 cost)
        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "AAPL",
                tradeType = "Buying shares",
                date = now - 1000,
                contracts = 10,
                strikePrice = 150.0,
                fees = 0.0
            )
        )
        val watchlist = listOf(
            WatchlistTicker(symbol = "AAPL", companyName = "Apple Inc.", livePrice = 160.0)
        )

        val summary = useCase(
            watchlist = watchlist,
            trades = trades,
            baseCurrency = "USD",
            now = now
        )

        // Implicit deposit covers $1,500 shortfall
        assertEquals(1500.0, summary.implicitCashDeposits, 0.01)
        // Cash remains 0.0 (all used to purchase shares)
        assertEquals(0.0, summary.totalCashBalance, 0.01)
        assertEquals(0.0, summary.buyingPower, 0.01)
        // Market value = 10 * 160 = $1,600
        assertEquals(1600.0, summary.totalStockMarketValue, 0.01)
        // Total Equity = Cash ($0) + Stock Market Value ($1,600) = $1,600
        assertEquals(1600.0, summary.totalEquity, 0.01)
        // Unrealized P&L = (160 - 150) * 10 = $100
        assertEquals(100.0, summary.aggregateUnrealizedPnL, 0.01)
    }

    @Test
    fun testSellingSharesUpdatesCashAndBuyingPower() {
        val now = System.currentTimeMillis()

        // User bought 10 AAPL @ $150 (unfunded -> implicit $1500), then sold 5 AAPL @ $170 ($850 proceeds)
        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "AAPL",
                tradeType = "Buying shares",
                date = now - 2000,
                contracts = 10,
                strikePrice = 150.0,
                fees = 0.0
            ),
            TradeEntity(
                id = 2,
                ticker = "AAPL",
                tradeType = "Selling shares",
                date = now - 1000,
                contracts = 5,
                strikePrice = 170.0,
                fees = 5.0 // $845 net proceeds
            )
        )
        val watchlist = listOf(
            WatchlistTicker(symbol = "AAPL", companyName = "Apple Inc.", livePrice = 160.0)
        )

        val summary = useCase(
            watchlist = watchlist,
            trades = trades,
            baseCurrency = "USD",
            now = now
        )

        // Cash receives $845 net proceeds
        assertEquals(845.0, summary.totalCashBalance, 0.01)
        assertEquals(845.0, summary.buyingPower, 0.01)
        // Remaining shares = 5 AAPL @ $160 = $800 market value
        assertEquals(800.0, summary.totalStockMarketValue, 0.01)
        // Total Equity = Cash ($845) + Market Value ($800) = $1,645
        assertEquals(1645.0, summary.totalEquity, 0.01)
    }

    @Test
    fun testManualCashDepositUpdatesCashBuyingPowerAndTotalEquity() {
        val now = System.currentTimeMillis()

        val trades = listOf(
            // Deposit $5,000 cash
            TradeEntity(
                id = 1,
                ticker = "CASH",
                tradeType = "Deposit",
                date = now - 3000,
                contracts = 1,
                strikePrice = 0.0,
                premiumPerShare = 5000.0,
                netCreditDebit = 5000.0
            ),
            // Buy 10 MSFT @ $200 = $2,000
            TradeEntity(
                id = 2,
                ticker = "MSFT",
                tradeType = "Buying shares",
                date = now - 2000,
                contracts = 10,
                strikePrice = 200.0,
                fees = 0.0
            ),
            // Collect $150 option premium
            TradeEntity(
                id = 3,
                ticker = "MSFT",
                tradeType = "Sell CC",
                date = now - 1000,
                contracts = 1,
                strikePrice = 220.0,
                premiumPerShare = 1.50,
                netCreditDebit = 150.0
            )
        )
        val watchlist = listOf(
            WatchlistTicker(symbol = "MSFT", companyName = "Microsoft", livePrice = 210.0)
        )

        val summary = useCase(
            watchlist = watchlist,
            trades = trades,
            baseCurrency = "USD",
            now = now
        )

        // Cash: 5000 (deposit) - 2000 (stock buy) + 150 (option premium) = 3150
        assertEquals(3150.0, summary.totalCashBalance, 0.01)
        assertEquals(3150.0, summary.buyingPower, 0.01)
        assertEquals(5000.0, summary.manualCashDeposits, 0.01)
        assertEquals(0.0, summary.implicitCashDeposits, 0.01) // fully funded
        // Stock market value = 10 * 210 = 2100
        assertEquals(2100.0, summary.totalStockMarketValue, 0.01)
        // Total Equity = Cash (3150) + Stock (2100) = 5250
        assertEquals(5250.0, summary.totalEquity, 0.01)
        assertEquals(150.0, summary.aggregatePremiums, 0.01)
        // Unrealized P&L = (210 - 200) * 10 = 100
        assertEquals(100.0, summary.aggregateUnrealizedPnL, 0.01)
        // Total P&L = 150 + 100 = 250
        assertEquals(250.0, summary.aggregateTotalPnL, 0.01)
    }

    @Test
    fun testCspCollateralLockingReducesBuyingPower() {
        val now = System.currentTimeMillis()
        val futureExpiry = now + 86400000L * 30

        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "CASH",
                tradeType = "Deposit",
                date = now - 2000,
                contracts = 1,
                strikePrice = 0.0,
                premiumPerShare = 10000.0,
                netCreditDebit = 10000.0
            ),
            TradeEntity(
                id = 2,
                ticker = "NVDA",
                tradeType = "Sell CSP",
                date = now - 1000,
                contracts = 1,
                strikePrice = 80.0, // 1 * 100 * 80 = $8,000 locked
                premiumPerShare = 2.0,
                fees = 1.0,
                netCreditDebit = 199.0,
                expiryDate = futureExpiry,
                isClosed = false
            )
        )
        val watchlist = listOf(
            WatchlistTicker(symbol = "NVDA", companyName = "Nvidia", livePrice = 85.0)
        )

        val summary = useCase(
            watchlist = watchlist,
            trades = trades,
            baseCurrency = "USD",
            now = now
        )

        // Cash: 10000 + 199 = 10199
        assertEquals(10199.0, summary.totalCashBalance, 0.01)
        // CSP Collateral locked = 8000
        assertEquals(8000.0, summary.totalCspLocked, 0.01)
        // Buying Power = 10199 - 8000 = 2199
        assertEquals(2199.0, summary.buyingPower, 0.01)
        // Total Equity = 10199 (Cash) + 0 (Stock) = 10199
        assertEquals(10199.0, summary.totalEquity, 0.01)
    }

    @Test
    fun testAssignmentWithPartialCashAddsShortfallBeforehand() {
        val now = System.currentTimeMillis()

        val trades = listOf(
            // User deposited $2,000 cash
            TradeEntity(
                id = 1,
                ticker = "CASH",
                tradeType = "Deposit",
                date = now - 3000,
                contracts = 1,
                strikePrice = 0.0,
                premiumPerShare = 2000.0,
                netCreditDebit = 2000.0
            ),
            // User assigned 1 contract @ $50 strike = $5,000 cost
            TradeEntity(
                id = 2,
                ticker = "KO",
                tradeType = "Assignment",
                date = now - 1000,
                contracts = 1,
                strikePrice = 50.0,
                fees = 0.0
            )
        )
        val watchlist = listOf(
            WatchlistTicker(symbol = "KO", companyName = "Coca-Cola", livePrice = 52.0)
        )

        val summary = useCase(
            watchlist = watchlist,
            trades = trades,
            baseCurrency = "USD",
            now = now
        )

        // Shortfall was $5,000 - $2,000 = $3,000
        assertEquals(3000.0, summary.implicitCashDeposits, 0.01)
        assertEquals(2000.0, summary.manualCashDeposits, 0.01)
        // Cash = 0.0 after assignment
        assertEquals(0.0, summary.totalCashBalance, 0.01)
        assertEquals(0.0, summary.buyingPower, 0.01)
        // Stock market value = 100 * 52 = $5,200
        assertEquals(5200.0, summary.totalStockMarketValue, 0.01)
        // Total Equity = $5,200
        assertEquals(5200.0, summary.totalEquity, 0.01)
    }
}
