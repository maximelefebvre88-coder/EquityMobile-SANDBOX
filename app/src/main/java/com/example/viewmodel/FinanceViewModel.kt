package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CalculatorSnapshot
import com.example.domain.model.PortfolioSummary
import com.example.domain.model.TradeEntity
import com.example.domain.model.WatchlistTicker
import com.example.domain.model.FmpSearchResponse
import com.example.domain.usecase.FinanceUseCases
import com.example.EquityIQApplication
import com.example.ui.screens.calculateBalanceSheetHealthScore
import com.example.ui.screens.calculateProfitQualityRankScore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class ApiKeyTestState {
    object Idle : ApiKeyTestState()
    object Testing : ApiKeyTestState()
    data class Success(val message: String) : ApiKeyTestState()
    data class Error(val message: String) : ApiKeyTestState()
}

class FinanceViewModel(
    application: Application,
    private val useCases: FinanceUseCases
) : AndroidViewModel(application) {

    // Secondary constructor for testing / backward compatibility
    constructor(application: Application) : this(
        application = application,
        useCases = (application as? EquityIQApplication)?.container?.financeUseCases
            ?: FinanceUseCases.createDefault(
                application,
                com.example.data.repository.FinanceRepositoryImpl(application)
            )
    )

    // Live state flows
    val watchlist: StateFlow<List<WatchlistTicker>> = useCases.getWatchlist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTrades: StateFlow<List<TradeEntity>> = useCases.getAllTrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings State
    val apiKeyFlow = MutableStateFlow(useCases.repository.getApiKey())
    val geminiApiKeyFlow = MutableStateFlow(useCases.repository.getGeminiApiKey())
    val currencyFlow = MutableStateFlow(useCases.repository.getCurrency()) // "CAD" or "USD"
    val riskFreeRateFlow = MutableStateFlow(useCases.repository.getRiskFreeRate())
    val riskPremiumFlow = MutableStateFlow(useCases.repository.getRiskPremium())

    // API Key Testing Diagnostic State
    val finnhubTestState = MutableStateFlow<ApiKeyTestState>(ApiKeyTestState.Idle)
    val geminiTestState = MutableStateFlow<ApiKeyTestState>(ApiKeyTestState.Idle)


    // Active Calculator Ticker State
    val selectedCalculatorTicker = MutableStateFlow("AAPL")
    
    // UI Loading state
    val tickerSyncing = MutableStateFlow<String?>(null)
    val isSyncingAll = MutableStateFlow(false)
    val syncError = MutableStateFlow<String?>(null)

    // Interactive Search Autocomplete State
    val searchResults = MutableStateFlow<List<FmpSearchResponse>>(emptyList())
    val isSearching = MutableStateFlow(false)

    init {
        // Automatically save settings adjustments back to secure storage
        viewModelScope.launch {
            currencyFlow.collect { useCases.repository.saveCurrency(it) }
        }
        viewModelScope.launch {
            riskFreeRateFlow.collect { useCases.repository.saveRiskFreeRate(it) }
        }
        viewModelScope.launch {
            riskPremiumFlow.collect { useCases.repository.saveRiskPremium(it) }
        }
        // Automatic Error Dismissal: gently clears any persistent "Sync Alert" banner from the user interface after 10 seconds of inactivity
        viewModelScope.launch {
            syncError.collectLatest { error ->
                if (error != null) {
                    kotlinx.coroutines.delay(10000L)
                    syncError.value = null
                }
            }
        }
        // Auto-reset on Ticker Navigation: Whenever you switch between different stocks on your watchlist, flushes any stale sync alert states
        viewModelScope.launch {
            selectedCalculatorTicker.collect {
                syncError.value = null
            }
        }
        // Automatically ensure any unpriced ticker in the watchlist gets its live price synced with cooldown
        val unpricedCooldownMap = java.util.concurrent.ConcurrentHashMap<String, Long>()
        viewModelScope.launch {
            watchlist.collect { list ->
                val now = System.currentTimeMillis()
                val unpriced = list.filter { 
                    it.livePrice <= 0.0 && 
                    it.symbol.isNotBlank() && 
                    it.symbol != "CASH" &&
                    (now - (unpricedCooldownMap[it.symbol.uppercase().trim()] ?: 0L)) > 60_000L
                }
                if (unpriced.isNotEmpty() && !isSyncingAll.value && tickerSyncing.value == null) {
                    unpriced.forEach { ticker ->
                        val sym = ticker.symbol.uppercase().trim()
                        unpricedCooldownMap[sym] = System.currentTimeMillis()
                        try {
                            useCases.syncTickerData(sym, force = false)
                        } catch (e: Exception) {
                            // Non-fatal background sync
                        }
                    }
                }
            }
        }
        // Automatically save live trade tickers in the ticker navigation line
        viewModelScope.launch {
            allTrades.collect { trades ->
                val now = System.currentTimeMillis()
                val uniqueTickers = trades.map { it.ticker.uppercase().trim() }.distinct()
                val currentWatchlist = watchlist.value.map { it.symbol.uppercase().trim() }.toSet()
                
                uniqueTickers.forEach { ticker ->
                    if (currentWatchlist.contains(ticker)) return@forEach
                    
                    val tickerTrades = trades.filter { it.ticker.equals(ticker, ignoreCase = true) }
                    val watchlistObj = watchlist.value.find { it.symbol.equals(ticker, ignoreCase = true) }
                    val livePrice = watchlistObj?.livePrice ?: 0.0
                    
                    var shares = 0
                    tickerTrades.forEach {
                        when (it.tradeType) {
                            "Assignment" -> shares += it.contracts * 100
                            "Called Away" -> shares -= it.contracts * 100
                            "Buying shares" -> shares += it.contracts
                            "Selling shares" -> shares -= it.contracts
                            "Sell CSP" -> {
                                when (it.manualOutcome) {
                                    "ASSIGNED" -> {
                                        shares += it.contracts * 100
                                    }
                                    "EXPIRED_WORTHLESS" -> {
                                        // do nothing
                                    }
                                    else -> {
                                        val isExpired = it.expiryDate != null && it.expiryDate <= now
                                        if (!it.isClosed && isExpired && livePrice > 0.0 && livePrice < it.strikePrice) {
                                            shares += it.contracts * 100
                                        }
                                    }
                                }
                            }
                            "Sell CC" -> {
                                when (it.manualOutcome) {
                                    "CALLED_AWAY" -> {
                                        shares -= it.contracts * 100
                                    }
                                    "EXPIRED_WORTHLESS" -> {
                                        // do nothing
                                    }
                                    else -> {
                                        val isExpired = it.expiryDate != null && it.expiryDate <= now
                                        if (!it.isClosed && isExpired && livePrice > 0.0 && livePrice > it.strikePrice) {
                                            shares -= it.contracts * 100
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val holdsShares = shares > 0
                    
                    val hasActiveOption = tickerTrades.any { 
                        (it.tradeType == "Sell CSP" || it.tradeType == "Sell CC") && 
                        !it.isClosed && it.expiryDate != null && it.expiryDate > now 
                    }
                    
                    if (holdsShares || hasActiveOption) {
                        addTickerToWatchlist(ticker, ticker)
                    }
                }
            }
        }
    }

    // Currency Multiplier based on USD-based API and CAD display preferences
    val currencyMultiplier: StateFlow<Double> = currencyFlow
        .map { if (it == "CAD") 1.36 else 1.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.36)

    fun updateApiKey(key: String) {
        val clean = key.trim().removeSurrounding("\"").removeSurrounding("'")
        useCases.repository.saveApiKey(clean)
        apiKeyFlow.value = useCases.repository.getApiKey()
        finnhubTestState.value = ApiKeyTestState.Idle
    }

    fun updateGeminiApiKey(key: String) {
        val clean = key.trim().removeSurrounding("\"").removeSurrounding("'")
        useCases.repository.saveGeminiApiKey(clean)
        geminiApiKeyFlow.value = useCases.repository.getGeminiApiKey()
        geminiTestState.value = ApiKeyTestState.Idle
    }

    fun testFinnhubApiKey(key: String = "") {
        viewModelScope.launch {
            finnhubTestState.value = ApiKeyTestState.Testing
            val activeKey = if (key.isNotBlank()) key else useCases.repository.getApiKey()
            val res = useCases.repository.testFinnhubConnection(activeKey)
            res.onSuccess {
                finnhubTestState.value = ApiKeyTestState.Success(it)
            }.onFailure {
                finnhubTestState.value = ApiKeyTestState.Error(it.message ?: "Validation failed")
            }
        }
    }

    fun testGeminiApiKey(key: String = "") {
        viewModelScope.launch {
            geminiTestState.value = ApiKeyTestState.Testing
            val activeKey = if (key.isNotBlank()) key else useCases.repository.getGeminiApiKey()
            val res = useCases.repository.testGeminiConnection(activeKey)
            res.onSuccess {
                geminiTestState.value = ApiKeyTestState.Success(it)
            }.onFailure {
                geminiTestState.value = ApiKeyTestState.Error(it.message ?: "Validation failed")
            }
        }
    }


    fun updateCurrency(currency: String) {
        currencyFlow.value = currency
    }

    fun updateRiskFreeRate(rate: Float) {
        riskFreeRateFlow.value = rate
    }

    fun updateRiskPremium(premium: Float) {
        riskPremiumFlow.value = premium
    }

    // Watchlist Updates
    fun addTickerToWatchlist(symbol: String, name: String) {
        viewModelScope.launch {
            useCases.addTicker(symbol, name)
            // Immediately sync real-time price for the added ticker
            syncTicker(symbol, force = true)
        }
    }

    fun removeTickerFromWatchlist(symbol: String) {
        viewModelScope.launch {
            useCases.removeTicker(symbol)
        }
    }

    fun swapWatchlistItems(symbol1: String, symbol2: String) {
        viewModelScope.launch {
            useCases.repository.swapWatchlistItems(symbol1, symbol2)
        }
    }

    fun updateWatchlistOrder(orderedSymbols: List<String>) {
        viewModelScope.launch {
            useCases.updateWatchlistOrder(orderedSymbols)
        }
    }

    fun updateTickerManualCostBasis(symbol: String, costBasis: Double?) {
        viewModelScope.launch {
            useCases.updateManualCostBasis(symbol, costBasis)
        }
    }

    fun updateTickerTargetPrice(symbol: String, targetPrice: Double?) {
        viewModelScope.launch {
            useCases.updateTargetPrice(symbol, targetPrice)
        }
    }

    fun updateTickerTargetYield(symbol: String, targetYield: Double?) {
        viewModelScope.launch {
            useCases.updateTargetYield(symbol, targetYield)
        }
    }

    fun updateTickerTargets(symbol: String, targetPrice: Double?, targetYield: Double?) {
        viewModelScope.launch {
            useCases.updateTargetPriceAndYield(symbol, targetPrice, targetYield)
        }
    }

    // Search function with debounce
    fun searchSymbols(query: String) {
        if (query.length < 2) {
            searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            isSearching.value = true
            val results = useCases.searchTickers(query)
            searchResults.value = results
            isSearching.value = false
        }
    }

    // Sync metrics for a single ticker
    fun syncTicker(symbol: String, force: Boolean = false) {
        viewModelScope.launch {
            val sym = symbol.uppercase().trim()
            if (sym.isEmpty() || sym == "CASH") return@launch
            tickerSyncing.value = sym
            syncError.value = null
            try {
                useCases.syncTickerData(sym, force)
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to sync live data"
                syncError.value = when {
                    msg.contains("429") -> "Rate limit reached (HTTP 429). Please wait a moment before retrying."
                    msg.contains("404") -> "Resource not found (HTTP 404). Please verify the ticker symbol or API endpoint."
                    else -> msg
                }
            } finally {
                tickerSyncing.value = null
            }
        }
    }

    // Sync metrics and real-time prices for ALL tickers in the portfolio watchlist
    fun syncAllWatchlistTickers(force: Boolean = false) {
        viewModelScope.launch {
            if (isSyncingAll.value) return@launch
            isSyncingAll.value = true
            syncError.value = null
            try {
                val currentList = watchlist.value
                for (ticker in currentList) {
                    val sym = ticker.symbol.uppercase().trim()
                    if (sym.isNotEmpty() && sym != "CASH") {
                        try {
                            tickerSyncing.value = sym
                            useCases.syncTickerData(sym, force)
                        } catch (e: Exception) {
                            // Non-fatal per-ticker sync error; allow other tickers to continue syncing
                        }
                    }
                }
            } finally {
                tickerSyncing.value = null
                isSyncingAll.value = false
            }
        }
    }

    // Force synchronize everything using Gemini AI (including corporate finance baseline and estimated 5-10yr FCF growth)
    fun forceGeminiSync(symbol: String, force: Boolean = false) {
        viewModelScope.launch {
            tickerSyncing.value = symbol
            syncError.value = null
            try {
                useCases.forceGeminiSync(symbol, force)
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to sync AI-Powered baseline & valuation metrics"
                syncError.value = when {
                    msg.contains("429") -> "Rate limit reached (HTTP 429). The Gemini free quota limit is active. Please wait ~30-60 seconds before retrying."
                    msg.contains("404") -> "Gemini endpoint or ticker not found (HTTP 404). Trying next fallback model tier..."
                    else -> msg
                }
            } finally {
                tickerSyncing.value = null
            }
        }
    }

    // Snapshot stream for the active calculator ticker
    val activeCalculatorSnapshot: StateFlow<CalculatorSnapshot?> = selectedCalculatorTicker
        .flatMapLatest { ticker ->
            useCases.getCalculatorSnapshotFlow(ticker)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tickerScores: StateFlow<Map<String, Int>> = useCases.repository.getAllCalculatorSnapshotsFlow()
        .map { snapshots ->
            snapshots.associate { snap ->
                val bsScore = calculateBalanceSheetHealthScore(
                    cashOnHand = snap.cashOnHand,
                    longTermDebt = snap.ltDebt,
                    freeCashFlow = snap.ttmFcf,
                    interestCoverage = snap.interestCoverage
                )
                val dbFcfConversion = if (snap.ttmNetIncome != 0.0) {
                    (snap.ttmFcf / snap.ttmNetIncome) * 100.0
                } else {
                    0.0
                }
                val pqScore = calculateProfitQualityRankScore(
                    roic = snap.roicPercent,
                    fcfMargin = snap.fcfMarginPercent,
                    fcfConversion = dbFcfConversion
                )
                val totalScore = (bsScore + pqScore) / 2
                snap.symbol.uppercase().trim() to totalScore
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allCalculatorSnapshots: StateFlow<Map<String, CalculatorSnapshot>> = useCases.repository.getAllCalculatorSnapshotsFlow()
        .map { snapshots ->
            snapshots.associateBy { it.symbol.uppercase().trim() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Save calculator snapshot modifications
    fun updateCalculatorSnapshot(snapshot: CalculatorSnapshot) {
        viewModelScope.launch {
            useCases.updateCalculatorSnapshot(snapshot)
        }
    }

    suspend fun getCalculatorSnapshot(symbol: String): CalculatorSnapshot? {
        return useCases.getCalculatorSnapshot(symbol)
    }

    // Options logging action
    fun logTrade(
        ticker: String,
        tradeType: String,
        date: Long,
        contracts: Int,
        strikePrice: Double,
        premiumPerShare: Double,
        expiryDate: Long?,
        fees: Double,
        fcfYield: Double = 0.0,
        isClosed: Boolean = false,
        closePremium: Double = 0.0,
        closeFees: Double = 0.0,
        closeDate: Long? = null,
        manualOutcome: String = ""
    ) {
        viewModelScope.launch {
            // Net Credit/Debit calculation
            val isSell = tradeType == "Sell CSP" || tradeType == "Sell CC"
            val isBuyToClose = tradeType == "Buy to Close"
            val isAssignment = tradeType == "Assignment"
            val isCalledAway = tradeType == "Called Away"
            val isDeposit = tradeType == "Deposit"
            val isWithdrawal = tradeType == "Withdrawal"
            val isBuyingShares = tradeType == "Buying shares"
            val isSellingShares = tradeType == "Selling shares"

            val netCreditDebit = when {
                isClosed -> {
                    val initialCredit = if (isSell) (premiumPerShare * 100.0 * contracts) else 0.0
                    val closingDebit = (closePremium * 100.0 * contracts)
                    initialCredit - closingDebit - fees - closeFees
                }
                isSell -> (premiumPerShare * 100.0 * contracts) - fees
                isBuyToClose -> -(premiumPerShare * 100.0 * contracts) - fees
                isAssignment -> -(strikePrice * 100.0 * contracts) - fees
                isCalledAway -> (strikePrice * 100.0 * contracts) - fees
                isDeposit -> premiumPerShare
                isWithdrawal -> -premiumPerShare
                isBuyingShares -> -(strikePrice * contracts) - fees
                isSellingShares -> (strikePrice * contracts) - fees
                else -> 0.0
            }

            // Calculate Annualized Return %:
            var annReturn = 0.0
            if (isSell && strikePrice > 0.0) {
                val endOfPeriod = if (isClosed && closeDate != null) closeDate else expiryDate
                if (endOfPeriod != null) {
                    val dteMillis = endOfPeriod - date
                    val dteDays = (dteMillis / (24.0 * 60.0 * 60.0 * 1000.0)).coerceAtLeast(1.0)
                    annReturn = (netCreditDebit / (strikePrice * 100.0 * contracts)) * (365.0 / dteDays) * 100.0
                }
            } else if (isBuyToClose && strikePrice > 0.0 && expiryDate != null) {
                val dteMillis = expiryDate - date
                val dteDays = (dteMillis / (24.0 * 60.0 * 60.0 * 1000.0)).coerceAtLeast(1.0)
                annReturn = (netCreditDebit / (strikePrice * 100.0 * contracts)) * (365.0 / dteDays) * 100.0
            }

            val trade = TradeEntity(
                ticker = ticker.uppercase().trim(),
                tradeType = tradeType,
                date = date,
                contracts = contracts,
                strikePrice = strikePrice,
                premiumPerShare = premiumPerShare,
                expiryDate = expiryDate,
                fees = fees,
                netCreditDebit = netCreditDebit,
                annualizedReturn = annReturn,
                fcfYield = fcfYield,
                isClosed = isClosed,
                closePremium = closePremium,
                closeFees = closeFees,
                closeDate = closeDate,
                manualOutcome = manualOutcome
            )

            useCases.logTrade(trade)
            // Save/ensure the ticker is also in the watchlist (ticker navigation line) immediately
            val trimmedTicker = ticker.uppercase().trim()
            if (trimmedTicker != "CASH") {
                addTickerToWatchlist(trimmedTicker, trimmedTicker)
            }
        }
    }

    fun updateTrade(
        id: Int,
        ticker: String,
        tradeType: String,
        date: Long,
        contracts: Int,
        strikePrice: Double,
        premiumPerShare: Double,
        expiryDate: Long?,
        fees: Double,
        fcfYield: Double = 0.0,
        isClosed: Boolean = false,
        closePremium: Double = 0.0,
        closeFees: Double = 0.0,
        closeDate: Long? = null,
        manualOutcome: String = ""
    ) {
        viewModelScope.launch {
            val isSell = tradeType == "Sell CSP" || tradeType == "Sell CC"
            val isBuyToClose = tradeType == "Buy to Close"
            val isAssignment = tradeType == "Assignment"
            val isCalledAway = tradeType == "Called Away"
            val isDeposit = tradeType == "Deposit"
            val isWithdrawal = tradeType == "Withdrawal"
            val isBuyingShares = tradeType == "Buying shares"
            val isSellingShares = tradeType == "Selling shares"

            val netCreditDebit = when {
                isClosed -> {
                    val initialCredit = if (isSell) (premiumPerShare * 100.0 * contracts) else 0.0
                    val closingDebit = (closePremium * 100.0 * contracts)
                    initialCredit - closingDebit - fees - closeFees
                }
                isSell -> (premiumPerShare * 100.0 * contracts) - fees
                isBuyToClose -> -(premiumPerShare * 100.0 * contracts) - fees
                isAssignment -> -(strikePrice * 100.0 * contracts) - fees
                isCalledAway -> (strikePrice * 100.0 * contracts) - fees
                isDeposit -> premiumPerShare
                isWithdrawal -> -premiumPerShare
                isBuyingShares -> -(strikePrice * contracts) - fees
                isSellingShares -> (strikePrice * contracts) - fees
                else -> 0.0
            }

            var annReturn = 0.0
            if (isSell && strikePrice > 0.0) {
                val endOfPeriod = if (isClosed && closeDate != null) closeDate else expiryDate
                if (endOfPeriod != null) {
                    val dteMillis = endOfPeriod - date
                    val dteDays = (dteMillis / (24.0 * 60.0 * 60.0 * 1000.0)).coerceAtLeast(1.0)
                    annReturn = (netCreditDebit / (strikePrice * 100.0 * contracts)) * (365.0 / dteDays) * 100.0
                }
            } else if (isBuyToClose && strikePrice > 0.0 && expiryDate != null) {
                val dteMillis = expiryDate - date
                val dteDays = (dteMillis / (24.0 * 60.0 * 60.0 * 1000.0)).coerceAtLeast(1.0)
                annReturn = (netCreditDebit / (strikePrice * 100.0 * contracts)) * (365.0 / dteDays) * 100.0
            }

            val trade = TradeEntity(
                id = id,
                ticker = ticker.uppercase().trim(),
                tradeType = tradeType,
                date = date,
                contracts = contracts,
                strikePrice = strikePrice,
                premiumPerShare = premiumPerShare,
                expiryDate = expiryDate,
                fees = fees,
                netCreditDebit = netCreditDebit,
                annualizedReturn = annReturn,
                fcfYield = fcfYield,
                isClosed = isClosed,
                closePremium = closePremium,
                closeFees = closeFees,
                closeDate = closeDate,
                manualOutcome = manualOutcome
            )

            useCases.updateTrade(trade)
            val trimmedTicker = ticker.uppercase().trim()
            if (trimmedTicker != "CASH") {
                addTickerToWatchlist(trimmedTicker, trimmedTicker)
            }
        }
    }

    fun deleteLoggedTrade(id: Int) {
        viewModelScope.launch {
            useCases.deleteLoggedTrade(id)
        }
    }

    fun calculateEffectiveCostBasis(
        symbol: String,
        livePrice: Double,
        manuallyEnteredCostBasis: Double?,
        trades: List<TradeEntity>
    ): Double {
        return useCases.calculateEffectiveCostBasis(symbol, livePrice, manuallyEnteredCostBasis, trades)
    }

    fun calculatePortfolioSummary(
        watchlist: List<WatchlistTicker>,
        trades: List<TradeEntity>,
        snapshots: Map<String, CalculatorSnapshot> = emptyMap(),
        baseCurrency: String = "CAD"
    ): PortfolioSummary {
        return useCases.calculatePortfolioSummary(
            watchlist = watchlist,
            trades = trades,
            snapshots = snapshots,
            baseCurrency = baseCurrency
        )
    }

    // Financial formulas
    fun calculateDcfFairValue(
        fcfPerShare: Double,
        growthRate: Double,
        requiredReturn: Double,
        terminalGrowth: Double,
        highGrowthYears: Int,
        netCashPerShare: Double
    ): Double {
        return useCases.calculateDcfFairValue(fcfPerShare, growthRate, requiredReturn, terminalGrowth, highGrowthYears, netCashPerShare)
    }

    fun calculateMoatQualityAdjustedDcf(
        snapshot: CalculatorSnapshot
    ): Pair<Double, com.example.domain.model.DcfAdjustment> {
        val moatScore = com.example.domain.usecase.MoatQualityAdjuster.calculateMoatScore(snapshot.checkedQualitativeTitles)
        
        val bsScore = calculateBalanceSheetHealthScore(
            cashOnHand = snapshot.cashOnHand,
            longTermDebt = snapshot.ltDebt,
            freeCashFlow = snapshot.ttmFcf,
            interestCoverage = snapshot.interestCoverage
        )
        val dbFcfConversion = if (snapshot.ttmNetIncome != 0.0) {
            (snapshot.ttmFcf / snapshot.ttmNetIncome) * 100.0
        } else {
            0.0
        }
        val pqScore = calculateProfitQualityRankScore(
            roic = snapshot.roicPercent,
            fcfMargin = snapshot.fcfMarginPercent,
            fcfConversion = dbFcfConversion
        )
        val qualityScore = (bsScore + pqScore) / 2.0
        
        val riskFree = snapshot.riskFreeRate
        val riskPremium = snapshot.riskPremium
        val baseRequiredReturn = riskFree + riskPremium
        
        return useCases.calculateDcfFairValue.calculateWithMoatQuality(
            fcfPerShare = snapshot.fcfPerShare,
            growthRate = snapshot.fcfGrowthRate,
            baseRequiredReturn = baseRequiredReturn,
            terminalGrowth = snapshot.terminalGrowthRate,
            netCashPerShare = snapshot.netCashPerShare,
            moatScore = moatScore,
            qualityScore = qualityScore
        )
    }

    fun calculateBuffettShortcut(
        fcfPerShare: Double,
        fcfGrowthRate: Double,
        riskFreeRate: Double,
        riskPremium: Double,
        fundamentalGrowthRate: Double,
        netCashPerShare: Double
    ): Double {
        return useCases.calculateBuffettShortcut(fcfPerShare, fcfGrowthRate, riskFreeRate, riskPremium, fundamentalGrowthRate, netCashPerShare)
    }

    fun calculateMoatQualityAdjustedBuffett(
        snapshot: CalculatorSnapshot,
        adjustment: com.example.domain.model.DcfAdjustment
    ): Double {
        return useCases.calculateBuffettShortcut(
            fcfPerShare = snapshot.fcfPerShare,
            fcfGrowthRate = snapshot.fcfGrowthRate,
            riskFreeRate = snapshot.riskFreeRate,
            riskPremium = snapshot.riskPremium + adjustment.requiredRateOfReturnAdjustmentPercent,
            fundamentalGrowthRate = snapshot.fundamentalGrowthRate,
            netCashPerShare = snapshot.netCashPerShare
        )
    }

    // Firebase Sync & Config Integration
    val firebaseManager = (application as? EquityIQApplication)?.container?.firebaseManager
    val firebaseAuthStatusMessage = MutableStateFlow<String?>(null)
    val isFirebaseSyncing = MutableStateFlow(false)

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            val fm = firebaseManager
            if (fm == null) {
                firebaseAuthStatusMessage.value = "Firebase is not initialized."
                return@launch
            }
            isFirebaseSyncing.value = true
            firebaseAuthStatusMessage.value = "Authenticating with Google..."
            val r = fm.signInWithGoogleIdToken(idToken)
            isFirebaseSyncing.value = false
            if (r.isSuccess) {
                firebaseAuthStatusMessage.value = "Authenticated & Cloud Synced!"
            } else {
                firebaseAuthStatusMessage.value = "Authentication failed: ${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun syncWithCloud() {
        viewModelScope.launch {
            val fm = firebaseManager ?: return@launch
            isFirebaseSyncing.value = true
            firebaseAuthStatusMessage.value = "Syncing with cloud..."
            val res = fm.syncDataAcrossDevices()
            isFirebaseSyncing.value = false
            firebaseAuthStatusMessage.value = if (res.isSuccess) {
                "Sync Completed Successfully!"
            } else {
                "Sync Failed: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun signOutFirebase() {
        firebaseManager?.signOut()
        firebaseAuthStatusMessage.value = "Signed out successfully."
    }

    fun setFirebaseAuthStatusMessage(msg: String?) {
        firebaseAuthStatusMessage.value = msg
    }

    fun updateFirebaseConfig(apiKey: String, projectId: String, appId: String, clientId: String) {
        firebaseManager?.saveFirebaseConfig(apiKey, projectId, appId, clientId)
    }
}
