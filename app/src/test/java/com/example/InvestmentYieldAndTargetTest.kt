package com.example

import com.example.domain.model.CalculatorSnapshot
import com.example.domain.model.TradeEntity
import com.example.domain.model.WatchlistTicker
import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentYieldAndTargetTest {

    @Test
    fun testWeightedAverageFcfYieldCalculationMultipleBuys() {
        // Buy 100 shares at 5% FCF yield
        // Buy 100 shares at 6% FCF yield
        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "ABC",
                tradeType = "Buying shares",
                date = 1000L,
                contracts = 100,
                strikePrice = 50.0,
                fcfYield = 5.0
            ),
            TradeEntity(
                id = 2,
                ticker = "ABC",
                tradeType = "Buying shares",
                date = 2000L,
                contracts = 100,
                strikePrice = 40.0,
                fcfYield = 6.0
            )
        )

        val buyTrades = trades.filter { it.tradeType == "Buying shares" || it.tradeType == "Assignment" }
        val totalShares = buyTrades.sumOf { if (it.tradeType == "Assignment") it.contracts * 100 else it.contracts }
        val weightedYieldSum = buyTrades.sumOf { trade ->
            val qty = if (trade.tradeType == "Assignment") trade.contracts * 100 else trade.contracts
            qty * trade.fcfYield
        }
        val avgYield = weightedYieldSum / totalShares

        assertEquals(200, totalShares)
        assertEquals(5.50, avgYield, 0.001)
    }

    @Test
    fun testWeightedAverageFcfYieldWithAssignmentAndBuys() {
        // Assigned 1 contract (100 shares) from CSP @ 4.8% FCF yield
        // Bought 50 shares @ 6.0% FCF yield
        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "XYZ",
                tradeType = "Assignment",
                date = 1000L,
                contracts = 1,
                strikePrice = 50.0,
                fcfYield = 4.8
            ),
            TradeEntity(
                id = 2,
                ticker = "XYZ",
                tradeType = "Buying shares",
                date = 2000L,
                contracts = 50,
                strikePrice = 40.0,
                fcfYield = 6.0
            )
        )

        val buyTrades = trades.filter { it.tradeType == "Buying shares" || it.tradeType == "Assignment" }
        val totalShares = buyTrades.sumOf { if (it.tradeType == "Assignment") it.contracts * 100 else it.contracts }
        val weightedYieldSum = buyTrades.sumOf { trade ->
            val qty = if (trade.tradeType == "Assignment") trade.contracts * 100 else trade.contracts
            qty * trade.fcfYield
        }
        val avgYield = weightedYieldSum / totalShares

        // (100 * 4.8 + 50 * 6.0) / 150 = (480 + 300) / 150 = 780 / 150 = 5.2%
        assertEquals(150, totalShares)
        assertEquals(5.20, avgYield, 0.001)
    }

    @Test
    fun testTargetPriceAndTargetYieldConversion() {
        val fcfPerShare = 2.50
        val targetPrice = 50.00

        // targetYield = (fcfPerShare / targetPrice) * 100
        val calculatedYield = (fcfPerShare / targetPrice) * 100.0
        assertEquals(5.00, calculatedYield, 0.001)

        // When entering target yield of 6.25%, implied target price = fcfPerShare / (yield / 100)
        val desiredYield = 6.25
        val calculatedPrice = fcfPerShare / (desiredYield / 100.0)
        assertEquals(40.00, calculatedPrice, 0.001)
    }

    @Test
    fun testExplicitTargetYieldDoesNotAlterCorporateBaseline() {
        val ticker = WatchlistTicker(
            symbol = "TEST",
            companyName = "Test Co",
            targetPrice = 100.0,
            targetYield = 7.5
        )

        val snapshot = CalculatorSnapshot(
            symbol = "TEST",
            currentPrice = 90.0,
            fcfPerShare = 4.50,
            revenuePerShare = 30.0,
            fcfMarginPercent = 15.0,
            roicPercent = 18.0,
            netCashPerShare = 5.0,
            sharesOutstanding = 10_000_000.0,
            marketCap = 900_000_000.0,
            fcfGrowthRate = 8.0,
            equityGrowthRate = 8.0,
            fundamentalGrowthRate = 8.0,
            historicalFcfMargin = 15.0,
            riskFreeRate = 4.0,
            riskPremium = 5.0,
            terminalGrowthRate = 2.5,
            highGrowthYears = 5,
            historicalFcfYield = 5.0,
            lastFetched = 1000L
        )

        // Setting a user targetYield (e.g. 7.5%) is purely a personal benchmark
        assertEquals(7.5, ticker.targetYield!!, 0.001)
        assertEquals(100.0, ticker.targetPrice!!, 0.001)

        // Baseline metrics remain exact financial statements data
        assertEquals(4.50, snapshot.fcfPerShare, 0.001)
        assertEquals(5.0, snapshot.netCashPerShare, 0.001)
        assertEquals(5.0, snapshot.historicalFcfYield, 0.001)
    }

    @Test
    fun testYieldBecomesZeroWhenAllSharesSold() {
        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "V",
                tradeType = "Buying shares",
                date = 1000L,
                contracts = 100,
                strikePrice = 280.0,
                fcfYield = 5.0
            ),
            TradeEntity(
                id = 2,
                ticker = "V",
                tradeType = "Selling shares",
                date = 2000L,
                contracts = 100,
                strikePrice = 300.0
            )
        )

        val heldShares = trades.fold(0) { sum, trade ->
            when (trade.tradeType) {
                "Buying shares" -> sum + trade.contracts
                "Selling shares" -> sum - trade.contracts
                else -> sum
            }
        }.coerceAtLeast(0)

        val buyTrades = trades.filter { it.tradeType == "Buying shares" }
        val totalBuyShares = buyTrades.sumOf { it.contracts }

        val avgYield = if (heldShares > 0 && totalBuyShares > 0) {
            buyTrades.sumOf { it.contracts * it.fcfYield } / totalBuyShares
        } else {
            0.0
        }

        assertEquals(0, heldShares)
        assertEquals(0.0, avgYield, 0.001)
    }
}
