package com.example

import com.example.domain.model.TradeEntity
import com.example.domain.usecase.CalculateEffectiveCostBasisUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateEffectiveCostBasisUseCaseTest {

    private val useCase = CalculateEffectiveCostBasisUseCase()

    @Test
    fun testMnstScenarioWithAssignedSharesAndOpenCsp() {
        val now = System.currentTimeMillis()
        val futureExpiry = now + 86400000L * 30

        // User collected $208.02 in past CSP sold, assigned 200 shares at $48.75 (even if marked closed or active).
        // Today stock price is $43.39, sold a new CSP at 0.65 for a $42.00 strike (1 contract, netCredit = $64.01).
        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "MNST",
                tradeType = "Sell CSP",
                date = now - 86400000L * 60,
                contracts = 2,
                strikePrice = 48.75,
                premiumPerShare = 1.05,
                netCreditDebit = 208.02,
                isClosed = true, // Marked closed as option trade finished
                manualOutcome = "ASSIGNED"
            ),
            TradeEntity(
                id = 2,
                ticker = "MNST",
                tradeType = "Sell CSP",
                date = now,
                contracts = 1,
                strikePrice = 42.00,
                premiumPerShare = 0.65,
                fees = 0.99,
                netCreditDebit = 64.01,
                expiryDate = futureExpiry,
                isClosed = false
            )
        )

        val costBasis = useCase(
            symbol = "MNST",
            livePrice = 43.39,
            manuallyEnteredCostBasis = null,
            trades = trades
        )

        // Total buy cost: 200 * 48.75 = $9750. Total shares = 200. Avg buy price = 48.75.
        // Total premium collected = 208.02 + 64.01 = 272.03.
        // Divisor = 200 shares.
        // Premium discount per share = 272.03 / 200 = 1.36015.
        // Effective cost basis = 48.75 - 1.36015 = 47.38985.
        assertEquals(47.38985, costBasis, 0.001)

        // Total P&L: (livePrice - effectiveCostBasis) * totalSharesHeld
        // = (43.39 - 47.38985) * 200 = -3.99985 * 200 = -799.97.
        val totalPnL = (43.39 - costBasis) * 200
        assertEquals(-799.97, totalPnL, 0.01)
    }

    @Test
    fun testMnstAutoAssignedExpiredItmWithNewOpenCsp() {
        val now = System.currentTimeMillis()
        val pastExpiry = now - 86400000L * 5
        val futureExpiry = now + 86400000L * 30

        // Past trade expired 5 days ago with strike 48.75 > live price 43.39 (auto assigned)
        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "MNST",
                tradeType = "Sell CSP",
                date = now - 86400000L * 35,
                contracts = 2,
                strikePrice = 48.75,
                premiumPerShare = 1.05,
                netCreditDebit = 208.02,
                expiryDate = pastExpiry,
                isClosed = false,
                manualOutcome = "" // Auto
            ),
            TradeEntity(
                id = 2,
                ticker = "MNST",
                tradeType = "Sell CSP",
                date = now,
                contracts = 1,
                strikePrice = 42.00,
                premiumPerShare = 0.65,
                fees = 0.99,
                netCreditDebit = 64.01,
                expiryDate = futureExpiry,
                isClosed = false
            )
        )

        val costBasis = useCase(
            symbol = "MNST",
            livePrice = 43.39,
            manuallyEnteredCostBasis = null,
            trades = trades
        )

        assertEquals(47.38985, costBasis, 0.001)
        val totalPnL = (43.39 - costBasis) * 200
        assertEquals(-799.97, totalPnL, 0.01)
    }

    @Test
    fun testOpenCspOnlyWithoutAssignedShares() {
        val now = System.currentTimeMillis()
        val futureExpiry = now + 86400000L * 30

        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "AAPL",
                tradeType = "Sell CSP",
                date = now,
                contracts = 1,
                strikePrice = 150.00,
                premiumPerShare = 2.00,
                fees = 1.00,
                netCreditDebit = 199.00,
                expiryDate = futureExpiry,
                isClosed = false
            )
        )

        val costBasis = useCase(
            symbol = "AAPL",
            livePrice = 155.00,
            manuallyEnteredCostBasis = null,
            trades = trades
        )

        // Base price = 150.00. Divisor = 100.
        // Premium discount = 199.00 / 100 = 1.99.
        // Effective cost basis = 150.00 - 1.99 = 148.01.
        assertEquals(148.01, costBasis, 0.001)
    }

    @Test
    fun testVisaScenarioWithBoughtSharesAndExcessPremium() {
        val now = System.currentTimeMillis()

        // User bought 7 shares of Visa at $306.55, collected $2816.00 in premium
        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "V",
                tradeType = "Buying shares",
                date = now - 86400000L * 60,
                contracts = 7, // 7 shares
                strikePrice = 306.55,
                netCreditDebit = -2145.85
            ),
            TradeEntity(
                id = 2,
                ticker = "V",
                tradeType = "Sell CSP",
                date = now - 86400000L * 30,
                contracts = 1,
                strikePrice = 300.00,
                premiumPerShare = 28.16,
                netCreditDebit = 2816.00,
                isClosed = true
            )
        )

        val detailed = useCase.calculateDetailed(
            symbol = "V",
            livePrice = 306.55,
            manuallyEnteredCostBasis = null,
            trades = trades
        )

        assertEquals(7, detailed.totalSharesHeld)
        assertEquals(306.55, detailed.avgBuyPrice, 0.001)
        assertEquals(0.0, detailed.currentCostBasis, 0.001)
        assertEquals(670.15, detailed.leftoverPremium, 0.001)
        // Future cost basis on realtime share price ($306.55) with $670.15 leftover premium over 100 shares ($6.7015/sh):
        assertEquals(299.8485, detailed.futureCostBasis, 0.001)
    }

    @Test
    fun testUserExampleTwoSharesBoughtWithExcessPremium() {
        val now = System.currentTimeMillis()

        // User collected $1000 in premium and bought 2 shares at $250
        val trades = listOf(
            TradeEntity(
                id = 1,
                ticker = "XYZ",
                tradeType = "Buying shares",
                date = now - 86400000L * 30,
                contracts = 2,
                strikePrice = 250.00,
                netCreditDebit = -500.00
            ),
            TradeEntity(
                id = 2,
                ticker = "XYZ",
                tradeType = "Sell CSP",
                date = now - 86400000L * 20,
                contracts = 1,
                strikePrice = 250.00,
                premiumPerShare = 10.00,
                netCreditDebit = 1000.00,
                isClosed = true
            )
        )

        val detailed = useCase.calculateDetailed(
            symbol = "XYZ",
            livePrice = 250.00,
            manuallyEnteredCostBasis = null,
            trades = trades
        )

        assertEquals(2, detailed.totalSharesHeld)
        assertEquals(250.00, detailed.avgBuyPrice, 0.001)
        assertEquals(0.0, detailed.currentCostBasis, 0.001)
        assertEquals(500.00, detailed.leftoverPremium, 0.001)
        // Future cost basis: 250 - (500 / 100) = 245.00
        assertEquals(245.00, detailed.futureCostBasis, 0.001)
    }
}
