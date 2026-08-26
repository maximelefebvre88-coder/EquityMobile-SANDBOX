package com.example.domain.usecase

import com.example.domain.model.TradeEntity

class CalculateEffectiveCostBasisUseCase {
    operator fun invoke(
        symbol: String,
        livePrice: Double,
        manuallyEnteredCostBasis: Double?,
        trades: List<TradeEntity>
    ): Double {
        if (manuallyEnteredCostBasis != null) {
            return manuallyEnteredCostBasis
        }

        val tickerTrades = trades.filter { it.ticker.equals(symbol, ignoreCase = true) }
        val now = System.currentTimeMillis()

        // 1. Total Shares: Assignment (+100 * contracts), Called Away (-100 * contracts), Buying shares (+contracts), Selling shares (-contracts)
        var totalSharesHeld = 0
        tickerTrades.forEach {
            when (it.tradeType) {
                "Assignment" -> totalSharesHeld += it.contracts * 100
                "Called Away" -> totalSharesHeld -= it.contracts * 100
                "Buying shares" -> totalSharesHeld += it.contracts
                "Selling shares" -> totalSharesHeld -= it.contracts
                "Sell CSP" -> {
                    val isClosed = it.isClosed
                    if (!isClosed) {
                        when (it.manualOutcome) {
                            "ASSIGNED" -> {
                                totalSharesHeld += it.contracts * 100
                            }
                            "EXPIRED_WORTHLESS" -> {
                                // do nothing
                            }
                            else -> {
                                val isExpired = it.expiryDate != null && it.expiryDate <= now
                                if (isExpired && livePrice > 0.0 && livePrice < it.strikePrice) {
                                    totalSharesHeld += it.contracts * 100
                                }
                            }
                        }
                    }
                }
                "Sell CC" -> {
                    val isClosed = it.isClosed
                    if (!isClosed) {
                        when (it.manualOutcome) {
                            "CALLED_AWAY" -> {
                                totalSharesHeld -= it.contracts * 100
                            }
                            "EXPIRED_WORTHLESS" -> {
                                // do nothing
                            }
                            else -> {
                                val isExpired = it.expiryDate != null && it.expiryDate <= now
                                if (isExpired && livePrice > 0.0 && livePrice > it.strikePrice) {
                                    totalSharesHeld -= it.contracts * 100
                                }
                            }
                        }
                    }
                }
            }
        }
        totalSharesHeld = totalSharesHeld.coerceAtLeast(0)

        // 2. Net premium collected from options (Sell CSP, BTC, Sell CC)
        val totalPremiumCollected = tickerTrades.filter { 
            it.tradeType == "Sell CSP" || it.tradeType == "Buy to Close" || it.tradeType == "Sell CC"
        }.sumOf { it.netCreditDebit }

        // 3. Assigned Cost Basics: sum of strike paid on assignment + buying shares
        var totalBuyShares = 0
        var totalBuyCost = 0.0
        tickerTrades.forEach {
            when (it.tradeType) {
                "Assignment" -> {
                    totalBuyShares += it.contracts * 100
                    totalBuyCost += it.strikePrice * 100.0 * it.contracts
                }
                "Buying shares" -> {
                    totalBuyShares += it.contracts
                    totalBuyCost += it.strikePrice * it.contracts
                }
                "Sell CSP" -> {
                    val isClosed = it.isClosed
                    if (!isClosed) {
                        when (it.manualOutcome) {
                            "ASSIGNED" -> {
                                totalBuyShares += it.contracts * 100
                                totalBuyCost += it.strikePrice * 100.0 * it.contracts
                            }
                            "EXPIRED_WORTHLESS" -> {
                                // do nothing
                            }
                            else -> {
                                val isExpired = it.expiryDate != null && it.expiryDate <= now
                                if (isExpired && livePrice > 0.0 && livePrice < it.strikePrice) {
                                    totalBuyShares += it.contracts * 100
                                    totalBuyCost += it.strikePrice * 100.0 * it.contracts
                                }
                            }
                        }
                    }
                }
            }
        }
        val avgBuyPrice = if (totalBuyShares > 0) totalBuyCost / totalBuyShares else 0.0
        val assignmentCost = avgBuyPrice * totalSharesHeld

        // 4. Determine if there is an active CSP
        val activeCSPs = tickerTrades.filter {
            it.tradeType == "Sell CSP" && !it.isClosed && (it.expiryDate == null || it.expiryDate > now)
        }
        val hasActiveCSP = activeCSPs.isNotEmpty()

        val basePrice = when {
            hasActiveCSP -> {
                // Calculate average strike price of active CSPs weighted by contracts
                val totalContracts = activeCSPs.sumOf { it.contracts }
                if (totalContracts > 0) {
                    activeCSPs.sumOf { it.strikePrice * it.contracts } / totalContracts
                } else {
                    activeCSPs.first().strikePrice
                }
            }
            totalSharesHeld > 0 -> {
                assignmentCost / totalSharesHeld
            }
            else -> {
                livePrice
            }
        }

        val divisor = when {
            hasActiveCSP -> {
                activeCSPs.sumOf { it.contracts } * 100.0
            }
            totalSharesHeld > 0 -> {
                totalSharesHeld.toDouble()
            }
            else -> {
                val lastOptionTrade = tickerTrades.lastOrNull {
                    it.tradeType == "Sell CSP" || it.tradeType == "Buy to Close" || it.tradeType == "Sell CC"
                }
                val contracts = lastOptionTrade?.contracts ?: 1
                contracts * 100.0
            }
        }

        val premiumDiscountPerShare = if (divisor > 0.0) {
            totalPremiumCollected / divisor
        } else {
            0.0
        }

        return (basePrice - premiumDiscountPerShare).coerceAtLeast(0.0)
    }
}
