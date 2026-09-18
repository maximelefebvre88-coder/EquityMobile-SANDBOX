package com.example.domain.usecase

import com.example.domain.model.TradeEntity

data class DetailedCostBasis(
    val currentCostBasis: Double,
    val futureCostBasis: Double,
    val leftoverPremium: Double,
    val avgBuyPrice: Double,
    val totalSharesHeld: Int,
    val totalPremiumCollected: Double,
    val hasActiveCSP: Boolean,
    val activeCspStrike: Double?
)

class CalculateEffectiveCostBasisUseCase {

    fun calculateDetailed(
        symbol: String,
        livePrice: Double,
        manuallyEnteredCostBasis: Double?,
        trades: List<TradeEntity>
    ): DetailedCostBasis {
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
                    when (it.manualOutcome) {
                        "ASSIGNED" -> {
                            totalSharesHeld += it.contracts * 100
                        }
                        "EXPIRED_WORTHLESS" -> {
                            // do nothing
                        }
                        else -> {
                            val isExpired = it.expiryDate != null && it.expiryDate <= now
                            if (!it.isClosed && isExpired && livePrice > 0.0 && livePrice < it.strikePrice) {
                                totalSharesHeld += it.contracts * 100
                            }
                        }
                    }
                }
                "Sell CC" -> {
                    when (it.manualOutcome) {
                        "CALLED_AWAY" -> {
                            totalSharesHeld -= it.contracts * 100
                        }
                        "EXPIRED_WORTHLESS" -> {
                            // do nothing
                        }
                        else -> {
                            val isExpired = it.expiryDate != null && it.expiryDate <= now
                            if (!it.isClosed && isExpired && livePrice > 0.0 && livePrice > it.strikePrice) {
                                totalSharesHeld -= it.contracts * 100
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
                            if (!it.isClosed && isExpired && livePrice > 0.0 && livePrice < it.strikePrice) {
                                totalBuyShares += it.contracts * 100
                                totalBuyCost += it.strikePrice * 100.0 * it.contracts
                            }
                        }
                    }
                }
            }
        }
        val avgBuyPrice = if (totalBuyShares > 0) totalBuyCost / totalBuyShares else 0.0

        // 4. Current Cost Basis (for held shares)
        val currentCostBasis = if (manuallyEnteredCostBasis != null) {
            manuallyEnteredCostBasis
        } else if (totalSharesHeld > 0) {
            val discountPerShare = totalPremiumCollected / totalSharesHeld.toDouble()
            (avgBuyPrice - discountPerShare).coerceAtLeast(0.0)
        } else {
            0.0
        }

        // 5. Leftover premium calculation:
        // Premium absorbed by current shares is min(totalPremiumCollected, costToAcquireHeldShares)
        val sharesCostToCover = if (manuallyEnteredCostBasis != null) {
            manuallyEnteredCostBasis * totalSharesHeld
        } else {
            avgBuyPrice * totalSharesHeld
        }
        val leftoverPremium = if (totalSharesHeld > 0) {
            (totalPremiumCollected - sharesCostToCover).coerceAtLeast(0.0)
        } else {
            totalPremiumCollected.coerceAtLeast(0.0)
        }

        // 6. Active CSP and Future Cost Basis
        val activeCSPs = tickerTrades.filter {
            it.tradeType == "Sell CSP" &&
            !it.isClosed &&
            it.manualOutcome != "EXPIRED_WORTHLESS" &&
            it.manualOutcome != "ASSIGNED" &&
            (it.expiryDate == null || it.expiryDate > now)
        }
        val hasActiveCSP = activeCSPs.isNotEmpty()
        val totalCspContracts = if (hasActiveCSP) activeCSPs.sumOf { it.contracts } else 0
        val activeCspStrike = if (hasActiveCSP) {
            if (totalCspContracts > 0) {
                activeCSPs.sumOf { it.strikePrice * it.contracts } / totalCspContracts
            } else {
                activeCSPs.first().strikePrice
            }
        } else null

        val futureBasePrice = when {
            hasActiveCSP -> activeCspStrike ?: 0.0
            else -> livePrice
        }

        val futureDivisor = when {
            hasActiveCSP -> (if (totalCspContracts > 0) totalCspContracts else 1) * 100.0
            else -> {
                val lastOptionTrade = tickerTrades.lastOrNull {
                    it.tradeType == "Sell CSP" || it.tradeType == "Buy to Close" || it.tradeType == "Sell CC"
                }
                val contracts = lastOptionTrade?.contracts ?: 1
                (if (contracts > 0) contracts else 1) * 100.0
            }
        }

        val futureDiscountPerShare = if (futureDivisor > 0.0) {
            leftoverPremium / futureDivisor
        } else {
            0.0
        }

        val futureCostBasis = if (futureBasePrice > 0.0) {
            (futureBasePrice - futureDiscountPerShare).coerceAtLeast(0.0)
        } else {
            0.0
        }

        return DetailedCostBasis(
            currentCostBasis = currentCostBasis,
            futureCostBasis = futureCostBasis,
            leftoverPremium = leftoverPremium,
            avgBuyPrice = avgBuyPrice,
            totalSharesHeld = totalSharesHeld,
            totalPremiumCollected = totalPremiumCollected,
            hasActiveCSP = hasActiveCSP,
            activeCspStrike = activeCspStrike
        )
    }

    operator fun invoke(
        symbol: String,
        livePrice: Double,
        manuallyEnteredCostBasis: Double?,
        trades: List<TradeEntity>
    ): Double {
        if (manuallyEnteredCostBasis != null) {
            return manuallyEnteredCostBasis
        }
        val detailed = calculateDetailed(symbol, livePrice, manuallyEnteredCostBasis, trades)
        return if (detailed.totalSharesHeld > 0) {
            detailed.currentCostBasis
        } else {
            detailed.futureCostBasis
        }
    }
}
