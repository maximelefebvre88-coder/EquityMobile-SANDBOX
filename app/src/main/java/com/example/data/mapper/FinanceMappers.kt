package com.example.data.mapper

import com.example.data.local.CalculatorSnapshotEntity
import com.example.data.local.TradeLogEntity
import com.example.data.local.WatchlistTickerEntity
import com.example.domain.model.CalculatorSnapshot
import com.example.domain.model.FmpSearchResponse
import com.example.domain.model.TradeEntity
import com.example.domain.model.WatchlistTicker

fun WatchlistTickerEntity.toDomain(): WatchlistTicker {
    return WatchlistTicker(
        symbol = symbol,
        companyName = companyName,
        livePrice = livePrice,
        lastFetched = lastFetched,
        manuallyEnteredCostBasis = manuallyEnteredCostBasis,
        targetPrice = targetPrice,
        logoUrl = logoUrl,
        displayOrder = displayOrder,
        changePercent = changePercent
    )
}

fun WatchlistTicker.toEntity(): WatchlistTickerEntity {
    return WatchlistTickerEntity(
        symbol = symbol,
        companyName = companyName,
        livePrice = livePrice,
        lastFetched = lastFetched,
        manuallyEnteredCostBasis = manuallyEnteredCostBasis,
        targetPrice = targetPrice,
        logoUrl = logoUrl,
        displayOrder = displayOrder,
        changePercent = changePercent
    )
}

fun TradeLogEntity.toDomain(): TradeEntity {
    return TradeEntity(
        id = id,
        ticker = ticker,
        tradeType = tradeType,
        date = date,
        contracts = contracts,
        strikePrice = strikePrice,
        premiumPerShare = premiumPerShare,
        expiryDate = expiryDate,
        fees = fees,
        netCreditDebit = netCreditDebit,
        annualizedReturn = annualizedReturn,
        fcfYield = fcfYield,
        isClosed = isClosed,
        closePremium = closePremium,
        closeFees = closeFees,
        closeDate = closeDate,
        manualOutcome = manualOutcome
    )
}

fun TradeEntity.toEntity(): TradeLogEntity {
    return TradeLogEntity(
        id = id,
        ticker = ticker,
        tradeType = tradeType,
        date = date,
        contracts = contracts,
        strikePrice = strikePrice,
        premiumPerShare = premiumPerShare,
        expiryDate = expiryDate,
        fees = fees,
        netCreditDebit = netCreditDebit,
        annualizedReturn = annualizedReturn,
        fcfYield = fcfYield,
        isClosed = isClosed,
        closePremium = closePremium,
        closeFees = closeFees,
        closeDate = closeDate,
        manualOutcome = manualOutcome
    )
}

fun CalculatorSnapshotEntity.toDomain(): CalculatorSnapshot {
    return CalculatorSnapshot(
        symbol = symbol,
        currentPrice = currentPrice,
        fcfPerShare = fcfPerShare,
        revenuePerShare = revenuePerShare,
        fcfMarginPercent = fcfMarginPercent,
        roicPercent = roicPercent,
        netCashPerShare = netCashPerShare,
        sharesOutstanding = sharesOutstanding,
        marketCap = marketCap,
        fcfGrowthRate = fcfGrowthRate,
        equityGrowthRate = equityGrowthRate,
        fundamentalGrowthRate = fundamentalGrowthRate,
        historicalFcfMargin = historicalFcfMargin,
        riskFreeRate = riskFreeRate,
        riskPremium = riskPremium,
        terminalGrowthRate = terminalGrowthRate,
        highGrowthYears = highGrowthYears,
        historicalFcfYield = historicalFcfYield,
        lastFetched = lastFetched,
        ttmRevenue = ttmRevenue,
        ttmFcf = ttmFcf,
        cashOnHand = cashOnHand,
        ltDebt = ltDebt,
        ttmNetIncome = ttmNetIncome,
        checkedQualitativeTitles = checkedQualitativeTitles
    )
}

fun CalculatorSnapshot.toEntity(): CalculatorSnapshotEntity {
    return CalculatorSnapshotEntity(
        symbol = symbol,
        currentPrice = currentPrice,
        fcfPerShare = fcfPerShare,
        revenuePerShare = revenuePerShare,
        fcfMarginPercent = fcfMarginPercent,
        roicPercent = roicPercent,
        netCashPerShare = netCashPerShare,
        sharesOutstanding = sharesOutstanding,
        marketCap = marketCap,
        fcfGrowthRate = fcfGrowthRate,
        equityGrowthRate = equityGrowthRate,
        fundamentalGrowthRate = fundamentalGrowthRate,
        historicalFcfMargin = historicalFcfMargin,
        riskFreeRate = riskFreeRate,
        riskPremium = riskPremium,
        terminalGrowthRate = terminalGrowthRate,
        highGrowthYears = highGrowthYears,
        historicalFcfYield = historicalFcfYield,
        lastFetched = lastFetched,
        ttmRevenue = ttmRevenue,
        ttmFcf = ttmFcf,
        cashOnHand = cashOnHand,
        ltDebt = ltDebt,
        ttmNetIncome = ttmNetIncome,
        checkedQualitativeTitles = checkedQualitativeTitles
    )
}

fun com.example.data.remote.FmpSearchResponse.toDomain(): FmpSearchResponse {
    return FmpSearchResponse(
        symbol = symbol,
        name = name,
        currency = currency,
        stockExchange = stockExchange
    )
}
