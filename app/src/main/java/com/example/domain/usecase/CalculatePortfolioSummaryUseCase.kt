package com.example.domain.usecase

import com.example.domain.model.CalculatorSnapshot
import com.example.domain.model.PortfolioSummary
import com.example.domain.model.TradeEntity
import com.example.domain.model.WatchlistTicker
import com.example.domain.util.getFxMultiplier
import com.example.domain.util.getTickerCurrency

class CalculatePortfolioSummaryUseCase {

    private sealed class CashEvent(val timestamp: Long, val priority: Int) {
        class Inflow(
            timestamp: Long,
            val amount: Double,
            val isManualDeposit: Boolean = false
        ) : CashEvent(timestamp, 0) // Inflows prioritized first on the same timestamp

        class Outflow(
            timestamp: Long,
            val amount: Double,
            val isSharePurchase: Boolean = false
        ) : CashEvent(timestamp, 1)
    }

    operator fun invoke(
        watchlist: List<WatchlistTicker>,
        trades: List<TradeEntity>,
        snapshots: Map<String, CalculatorSnapshot> = emptyMap(),
        baseCurrency: String = "CAD",
        now: Long = System.currentTimeMillis()
    ): PortfolioSummary {
        // 1. Build lookup for all tickers present in watchlist or trade logs
        val allSymbols = (watchlist.map { it.symbol.uppercase().trim() } +
                trades.map { it.ticker.uppercase().trim() })
            .asSequence()
            .filter { (it.isNotEmpty()) && (it != "CASH") }
            .distinct()
            .toList()

        val watchlistMap = watchlist.associateBy { it.symbol.uppercase().trim() }

        var totalStockMarketValue = 0.0
        var aggregatePremiums = 0.0
        var aggregateUnrealizedPnL = 0.0

        val cashEvents = mutableListOf<CashEvent>()

        // 2. Process ticker-by-ticker shares, market values, premiums, and unrealized P&L
        for (symbol in allSymbols) {
            val tickerObj = watchlistMap[symbol]
            val snapshotObj = snapshots[symbol]
            val tickerCurrency = getTickerCurrency(symbol)
            val fx = getFxMultiplier(tickerCurrency, baseCurrency)

            val livePrice = tickerObj?.livePrice?.takeIf { it > 0.0 }
                ?: snapshotObj?.currentPrice?.takeIf { it > 0.0 }
                ?: 0.0

            val tickerTrades = trades.filter { it.ticker.equals(symbol, ignoreCase = true) }

            // Shares calculation
            var totalSharesHeld = 0
            var totalBuyShares = 0
            var totalBuyCost = 0.0

            for (trade in tickerTrades) {
                when (trade.tradeType) {
                    "Assignment" -> {
                        val shares = trade.contracts * 100
                        totalSharesHeld += shares
                        totalBuyShares += shares
                        totalBuyCost += (trade.strikePrice * 100.0 * trade.contracts)
                    }
                    "Called Away" -> {
                        totalSharesHeld -= trade.contracts * 100
                    }
                    "Buying shares" -> {
                        val shares = trade.contracts
                        totalSharesHeld += shares
                        totalBuyShares += shares
                        totalBuyCost += (trade.strikePrice * trade.contracts)
                    }
                    "Selling shares" -> {
                        totalSharesHeld -= trade.contracts
                    }
                    "Sell CSP" -> {
                        when (trade.manualOutcome) {
                            "ASSIGNED" -> {
                                val shares = trade.contracts * 100
                                totalSharesHeld += shares
                                totalBuyShares += shares
                                totalBuyCost += (trade.strikePrice * 100.0 * trade.contracts)
                            }
                            "EXPIRED_WORTHLESS" -> {}
                            else -> {
                                val isExpired = trade.expiryDate != null && trade.expiryDate <= now
                                if (!trade.isClosed && isExpired && livePrice > 0.0 && livePrice < trade.strikePrice) {
                                    val shares = trade.contracts * 100
                                    totalSharesHeld += shares
                                    totalBuyShares += shares
                                    totalBuyCost += (trade.strikePrice * 100.0 * trade.contracts)
                                }
                            }
                        }
                    }
                    "Sell CC" -> {
                        when (trade.manualOutcome) {
                            "CALLED_AWAY" -> {
                                totalSharesHeld -= trade.contracts * 100
                            }
                            "EXPIRED_WORTHLESS" -> {}
                            else -> {
                                val isExpired = trade.expiryDate != null && trade.expiryDate <= now
                                if (!trade.isClosed && isExpired && livePrice > 0.0 && livePrice > trade.strikePrice) {
                                    totalSharesHeld -= trade.contracts * 100
                                }
                            }
                        }
                    }
                }
            }

            totalSharesHeld = totalSharesHeld.coerceAtLeast(0)

            // Market value of shares held
            if (totalSharesHeld > 0 && livePrice > 0.0) {
                val mVal = (totalSharesHeld * livePrice) * fx
                totalStockMarketValue += mVal
            }

            // Premiums for this ticker
            val tickerPrems = tickerTrades.filter {
                it.tradeType == "Sell CSP" || it.tradeType == "Buy to Close" || it.tradeType == "Sell CC"
            }.sumOf { it.netCreditDebit }
            aggregatePremiums += (tickerPrems * fx)

            // Unrealized P&L
            val avgBuyPrice = if (totalBuyShares > 0) totalBuyCost / totalBuyShares else 0.0
            val baseCostBasis = tickerObj?.manuallyEnteredCostBasis ?: avgBuyPrice
            if (totalSharesHeld > 0 && baseCostBasis > 0.0 && livePrice > 0.0) {
                val pnl = (livePrice - baseCostBasis) * totalSharesHeld * fx
                aggregateUnrealizedPnL += pnl
            }
        }

        // 3. Process all cash events across all trades chronologically
        for (trade in trades) {
            val tickerCurr = getTickerCurrency(trade.ticker)
            val fx = getFxMultiplier(tickerCurr, baseCurrency)

            when (trade.tradeType) {
                "Deposit" -> {
                    val amount = trade.netCreditDebit // in baseCurrency
                    if (amount > 0.0) {
                        cashEvents.add(CashEvent.Inflow(trade.date, amount, isManualDeposit = true))
                    }
                }
                "Withdrawal" -> {
                    val amount = kotlin.math.abs(trade.netCreditDebit)
                    if (amount > 0.0) {
                        cashEvents.add(CashEvent.Outflow(trade.date, amount))
                    }
                }
                "Sell CSP" -> {
                    // 1. Initial premium credit (or net if closed)
                    if (trade.isClosed && trade.closeDate != null && trade.closePremium > 0.0) {
                        val initialCredit = ((trade.premiumPerShare * 100.0 * trade.contracts) - trade.fees) * fx
                        val closingDebit = ((trade.closePremium * 100.0 * trade.contracts) + trade.closeFees) * fx
                        if (initialCredit > 0.0) {
                            cashEvents.add(CashEvent.Inflow(trade.date, initialCredit))
                        }
                        if (closingDebit > 0.0) {
                            cashEvents.add(CashEvent.Outflow(trade.closeDate, closingDebit))
                        }
                    } else {
                        val netCredit = trade.netCreditDebit * fx
                        if (netCredit >= 0.0) {
                            cashEvents.add(CashEvent.Inflow(trade.date, netCredit))
                        } else {
                            cashEvents.add(CashEvent.Outflow(trade.date, -netCredit))
                        }
                    }

                    // 2. Assignment share purchase (if assigned)
                    val isExplicitAssigned = trade.manualOutcome == "ASSIGNED"
                    val isAutoAssigned = !trade.isClosed &&
                            trade.manualOutcome.isEmpty() &&
                            trade.expiryDate != null &&
                            trade.expiryDate <= now &&
                            run {
                                val sym = trade.ticker.uppercase().trim()
                                val lp = watchlistMap[sym]?.livePrice?.takeIf { it > 0.0 }
                                    ?: snapshots[sym]?.currentPrice?.takeIf { it > 0.0 } ?: 0.0
                                lp > 0.0 && lp < trade.strikePrice
                            }

                    if (isExplicitAssigned || isAutoAssigned) {
                        val assignmentDate = trade.closeDate ?: trade.expiryDate ?: trade.date
                        val assignmentCost = (trade.strikePrice * 100.0 * trade.contracts) * fx
                        if (assignmentCost > 0.0) {
                            cashEvents.add(CashEvent.Outflow(assignmentDate, assignmentCost, isSharePurchase = true))
                        }
                    }
                }
                "Sell CC" -> {
                    // 1. Initial premium credit (or net if closed)
                    if (trade.isClosed && trade.closeDate != null && trade.closePremium > 0.0) {
                        val initialCredit = ((trade.premiumPerShare * 100.0 * trade.contracts) - trade.fees) * fx
                        val closingDebit = ((trade.closePremium * 100.0 * trade.contracts) + trade.closeFees) * fx
                        if (initialCredit > 0.0) {
                            cashEvents.add(CashEvent.Inflow(trade.date, initialCredit))
                        }
                        if (closingDebit > 0.0) {
                            cashEvents.add(CashEvent.Outflow(trade.closeDate, closingDebit))
                        }
                    } else {
                        val netCredit = trade.netCreditDebit * fx
                        if (netCredit >= 0.0) {
                            cashEvents.add(CashEvent.Inflow(trade.date, netCredit))
                        } else {
                            cashEvents.add(CashEvent.Outflow(trade.date, -netCredit))
                        }
                    }

                    // 2. Called away share sale (if called away)
                    val isExplicitCalledAway = trade.manualOutcome == "CALLED_AWAY"
                    val isAutoCalledAway = !trade.isClosed &&
                            trade.manualOutcome.isEmpty() &&
                            trade.expiryDate != null &&
                            trade.expiryDate <= now &&
                            run {
                                val sym = trade.ticker.uppercase().trim()
                                val lp = watchlistMap[sym]?.livePrice?.takeIf { it > 0.0 }
                                    ?: snapshots[sym]?.currentPrice?.takeIf { it > 0.0 } ?: 0.0
                                lp > 0.0 && lp > trade.strikePrice
                            }

                    if (isExplicitCalledAway || isAutoCalledAway) {
                        val calledAwayDate = trade.closeDate ?: trade.expiryDate ?: trade.date
                        val proceeds = (trade.strikePrice * 100.0 * trade.contracts) * fx
                        if (proceeds > 0.0) {
                            cashEvents.add(CashEvent.Inflow(calledAwayDate, proceeds))
                        }
                    }
                }
                "Buy to Close" -> {
                    val debit = kotlin.math.abs(trade.netCreditDebit) * fx
                    if (debit > 0.0) {
                        cashEvents.add(CashEvent.Outflow(trade.date, debit))
                    }
                }
                "Buying shares" -> {
                    val cost = (trade.strikePrice * trade.contracts + trade.fees) * fx
                    if (cost > 0.0) {
                        cashEvents.add(CashEvent.Outflow(trade.date, cost, isSharePurchase = true))
                    }
                }
                "Selling shares" -> {
                    val proceeds = (trade.strikePrice * trade.contracts - trade.fees) * fx
                    if (proceeds > 0.0) {
                        cashEvents.add(CashEvent.Inflow(trade.date, proceeds))
                    }
                }
                "Assignment" -> {
                    val cost = (trade.strikePrice * 100.0 * trade.contracts + trade.fees) * fx
                    if (cost > 0.0) {
                        cashEvents.add(CashEvent.Outflow(trade.date, cost, isSharePurchase = true))
                    }
                }
                "Called Away" -> {
                    val proceeds = (trade.strikePrice * 100.0 * trade.contracts - trade.fees) * fx
                    if (proceeds > 0.0) {
                        cashEvents.add(CashEvent.Inflow(trade.date, proceeds))
                    }
                }
            }
        }

        // 4. Sort cash events chronologically and execute running cash simulation
        cashEvents.sortWith(compareBy<CashEvent> { it.timestamp }.thenBy { it.priority })

        var runningCash = 0.0
        var manualDeposits = 0.0
        var implicitDeposits = 0.0

        for (event in cashEvents) {
            when (event) {
                is CashEvent.Inflow -> {
                    runningCash += event.amount
                    if (event.isManualDeposit) {
                        manualDeposits += event.amount
                    }
                }
                is CashEvent.Outflow -> {
                    if (event.isSharePurchase) {
                        // If shares are added by assignment or manually, but there was insufficient cash,
                        // assume the user added the money beforehand to fund the purchase.
                        if (runningCash < event.amount) {
                            val shortfall = event.amount - runningCash
                            implicitDeposits += shortfall
                            runningCash = 0.0
                        } else {
                            runningCash -= event.amount
                        }
                    } else {
                        runningCash -= event.amount
                    }
                }
            }
        }

        // 5. Calculate CSP locked collateral for open Cash-Secured Puts
        var totalCspLocked = 0.0
        for (symbol in allSymbols) {
            val tickerCurr = getTickerCurrency(symbol)
            val fx = getFxMultiplier(tickerCurr, baseCurrency)
            val tickerTrades = trades.filter { it.ticker.equals(symbol, ignoreCase = true) }

            val activeCsps = tickerTrades.filter {
                it.tradeType == "Sell CSP" &&
                        !it.isClosed &&
                        it.manualOutcome != "EXPIRED_WORTHLESS" &&
                        it.manualOutcome != "ASSIGNED" &&
                        (it.expiryDate == null || it.expiryDate > now)
            }
            val closures = tickerTrades.filter { it.tradeType == "Buy to Close" || it.tradeType == "Assignment" }

            activeCsps.forEach { csp ->
                val matchingClosures = closures.filter { it.strikePrice == csp.strikePrice && it.date >= csp.date }
                val closedContracts = matchingClosures.sumOf { it.contracts }
                val openContracts = (csp.contracts - closedContracts).coerceAtLeast(0)
                totalCspLocked += (openContracts * 100.0 * csp.strikePrice) * fx
            }
        }

        val totalCashBalance = runningCash
        val buyingPower = totalCashBalance - totalCspLocked
        val totalEquity = totalCashBalance + totalStockMarketValue
        val aggregateTotalPnL = aggregatePremiums + aggregateUnrealizedPnL

        return PortfolioSummary(
            totalEquity = totalEquity,
            totalCashBalance = totalCashBalance,
            buyingPower = buyingPower,
            totalStockMarketValue = totalStockMarketValue,
            aggregatePremiums = aggregatePremiums,
            aggregateUnrealizedPnL = aggregateUnrealizedPnL,
            aggregateTotalPnL = aggregateTotalPnL,
            totalCspLocked = totalCspLocked,
            manualCashDeposits = manualDeposits,
            implicitCashDeposits = implicitDeposits
        )
    }
}
