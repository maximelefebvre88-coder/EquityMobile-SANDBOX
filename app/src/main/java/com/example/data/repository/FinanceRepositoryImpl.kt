package com.example.data.repository

import android.content.Context
import com.example.EquityIQApplication
import com.example.data.CryptoManager
import com.example.data.local.*
import com.example.data.remote.*
import com.example.data.mapper.*
import com.example.domain.model.CalculatorSnapshot
import com.example.domain.model.FmpSearchResponse
import com.example.domain.model.TradeEntity
import com.example.domain.model.WatchlistTicker
import com.example.domain.repository.FinanceRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class FinanceRepositoryImpl(private val context: Context) : FinanceRepository {

    private val db = AppDatabase.getDatabase(context)
    private val tickerDao = db.tickerDao()
    private val tradeDao = db.tradeDao()
    private val calculatorSnapshotDao = db.calculatorSnapshotDao()
    private val cryptoManager = CryptoManager(context)

    private val lastRestSyncMap = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lastGeminiSyncMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // 5-Minute Cache TTL for Stale-While-Revalidate network optimization
    private val CACHE_TTL_MS = 5 * 60 * 1000L

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val finnhubOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val yahooOkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val yahooFinanceService: YahooFinanceService = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/")
        .client(yahooOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(YahooFinanceService::class.java)

    private val yahooFinanceBackupService: YahooFinanceService = Retrofit.Builder()
        .baseUrl("https://query2.finance.yahoo.com/")
        .client(yahooOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(YahooFinanceService::class.java)

    private val geminiOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiService: FmpApiService = Retrofit.Builder()
        .baseUrl("https://finnhub.io/api/v1/")
        .client(finnhubOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(FmpApiService::class.java)

    private val geminiApiService: GeminiApiService = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(geminiOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiApiService::class.java)

    private suspend fun fetchLiveQuoteFromYahoo(symbol: String): Triple<Double, Double?, String?>? {
        val sym = symbol.trim()
        val services = listOf(yahooFinanceService, yahooFinanceBackupService)
        for (service in services) {
            try {
                val res = service.getChart(sym)
                val meta = res.chart?.result?.firstOrNull()?.meta
                val price = meta?.regularMarketPrice ?: 0.0
                if (price > 0.0) {
                    val prevClose = meta?.previousClose ?: meta?.chartPreviousClose
                    val changePct = if (prevClose != null && prevClose > 0.0) {
                        ((price - prevClose) / prevClose) * 100.0
                    } else null
                    val compName = meta?.shortName ?: meta?.longName
                    return Triple(price, changePct, compName)
                }
            } catch (e: Exception) {
                // Try next endpoint
            }
        }
        return null
    }

    // Finnhub Request Rate-Limiting Throttler to strictly prevent 429 burst triggers on Free Tier (30 calls/min)
    private val finnhubThrottleMutex = kotlinx.coroutines.sync.Mutex()
    private var lastFinnhubRequestTime = 0L

    private suspend fun throttleFinnhubCall() {
        finnhubThrottleMutex.lock()
        try {
            val now = System.currentTimeMillis()
            val elapsed = now - lastFinnhubRequestTime
            if (elapsed < 1200L) {
                delay(1200L - elapsed)
            }
            lastFinnhubRequestTime = System.currentTimeMillis()
        } finally {
            finnhubThrottleMutex.unlock()
        }
    }

    // Secure settings getters/setters mapped via CryptoManager
    override fun getApiKey(): String {
        val userKey = cryptoManager.getApiKey().trim().removeSurrounding("\"").removeSurrounding("'")
        if (userKey.isNotEmpty()) {
            return userKey
        }
        val presetKey = com.example.BuildConfig.FINNHUB_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'")
        if (presetKey.isNotEmpty() && presetKey != "YOUR_FINNHUB_API_KEY_HERE" && !presetKey.startsWith("MY_FINNHUB")) {
            return presetKey
        }
        return ""
    }

    override fun getGeminiApiKey(): String {
        val userKey = cryptoManager.getGeminiApiKey().trim().removeSurrounding("\"").removeSurrounding("'")
        if (userKey.isNotEmpty()) {
            return userKey
        }
        val presetKey = com.example.BuildConfig.GEMINI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'")
        if (presetKey.isNotEmpty() && presetKey != "YOUR_GEMINI_API_KEY_HERE" && presetKey != "MY_GEMINI_API_KEY" && !presetKey.startsWith("MY_GEMINI")) {
            return presetKey
        }
        return ""
    }

    
    override fun saveApiKey(key: String) {
        cryptoManager.saveApiKey(key)
        syncPreferencesToFirebase()
    }
    
    override fun saveGeminiApiKey(key: String) {
        cryptoManager.saveGeminiApiKey(key)
        syncPreferencesToFirebase()
    }

    override fun getCurrency(): String = cryptoManager.getCurrency()
    override fun saveCurrency(currency: String) {
        cryptoManager.saveCurrency(currency)
        syncPreferencesToFirebase()
    }

    override fun getRiskFreeRate(): Float = cryptoManager.getRiskFreeRate()
    override fun saveRiskFreeRate(rate: Float) {
        cryptoManager.saveRiskFreeRate(rate)
        syncPreferencesToFirebase()
    }

    override fun getRiskPremium(): Float = cryptoManager.getRiskPremium()
    override fun saveRiskPremium(premium: Float) {
        cryptoManager.saveRiskPremium(premium)
        syncPreferencesToFirebase()
    }

    private fun syncPreferencesToFirebase() {
        val firebaseManager = (context.applicationContext as? com.example.EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            firebaseManager.syncPreferencesSingle(
                currency = cryptoManager.getCurrency(),
                riskFreeRate = cryptoManager.getRiskFreeRate(),
                riskPremium = cryptoManager.getRiskPremium(),
                apiKey = cryptoManager.getApiKey(),
                geminiApiKey = cryptoManager.getGeminiApiKey()
            )
        }
    }

    // Local DB Operations
    override fun getWatchlist(): Flow<List<WatchlistTicker>> {
        return tickerDao.getAllTickers().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun addTicker(symbol: String, name: String) = withContext(Dispatchers.IO) {
        val sym = symbol.uppercase().trim()
        val existing = tickerDao.getTickerBySymbol(sym)
        if (existing != null) {
            // Already in watchlist. Do not overwrite or erase targetPrice, costBasis, or displayOrder!
            if (name.isNotEmpty() && name != sym && (existing.companyName.isEmpty() || existing.companyName == sym)) {
                tickerDao.updatePrice(
                    symbol = sym,
                    price = existing.livePrice,
                    companyName = name,
                    timestamp = existing.lastFetched,
                    logoUrl = existing.logoUrl,
                    changePercent = existing.changePercent
                )
            }
            return@withContext
        }
        val maxOrder = tickerDao.getMaxDisplayOrder() ?: 0
        val entity = WatchlistTickerEntity(
            symbol = sym,
            companyName = name,
            livePrice = 0.0,
            lastFetched = 0L,
            displayOrder = maxOrder + 1
        )
        tickerDao.insert(entity)

        // Sync with Firebase
        val firebaseManager = (context.applicationContext as? com.example.EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            firebaseManager.syncWatchlistSingle(entity)
        }
    }

    override suspend fun removeTicker(symbol: String) = withContext(Dispatchers.IO) {
        tickerDao.delete(symbol)

        // Sync with Firebase
        val firebaseManager = (context.applicationContext as? com.example.EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            firebaseManager.removeWatchlistSingle(symbol)
        }
    }

    override suspend fun updateManualCostBasis(symbol: String, costBasis: Double?) = withContext(Dispatchers.IO) {
        tickerDao.updateCostBasis(symbol, costBasis)

        // Sync with Firebase
        val firebaseManager = (context.applicationContext as? com.example.EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            val ticker = tickerDao.getTickerBySymbol(symbol)
            if (ticker != null) {
                firebaseManager.syncWatchlistSingle(ticker)
            }
        }
    }

    override suspend fun updateTargetPrice(symbol: String, targetPrice: Double?) = withContext(Dispatchers.IO) {
        tickerDao.updateTargetPrice(symbol, targetPrice)

        // Sync with Firebase
        val firebaseManager = (context.applicationContext as? EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            val ticker = tickerDao.getTickerBySymbol(symbol)
            if (ticker != null) {
                firebaseManager.syncWatchlistSingle(ticker)
            }
        }
    }

    override suspend fun updateTargetYield(symbol: String, targetYield: Double?) = withContext(Dispatchers.IO) {
        tickerDao.updateTargetYield(symbol, targetYield)

        // Sync with Firebase
        val firebaseManager = (context.applicationContext as? EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            val ticker = tickerDao.getTickerBySymbol(symbol)
            if (ticker != null) {
                firebaseManager.syncWatchlistSingle(ticker)
            }
        }
    }

    override suspend fun updateTargetPriceAndYield(symbol: String, targetPrice: Double?, targetYield: Double?) = withContext(Dispatchers.IO) {
        tickerDao.updateTargetPriceAndYield(symbol, targetPrice, targetYield)

        // Sync with Firebase
        val firebaseManager = (context.applicationContext as? com.example.EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            val ticker = tickerDao.getTickerBySymbol(symbol)
            if (ticker != null) {
                firebaseManager.syncWatchlistSingle(ticker)
            }
        }
    }

    override suspend fun swapWatchlistItems(symbol1: String, symbol2: String) = withContext(Dispatchers.IO) {
        val ticker1 = tickerDao.getTickerBySymbol(symbol1)
        val ticker2 = tickerDao.getTickerBySymbol(symbol2)
        if (ticker1 != null && ticker2 != null) {
            val order1 = ticker1.displayOrder
            val order2 = ticker2.displayOrder
            if (order1 == order2) {
                // If they are equal (for example, old schema entities without display order set),
                // query and assign sequential unique orders to all existing tickers first.
                val allTickers = tickerDao.getAllTickersNonFlow()
                allTickers.forEachIndexed { index, ticker ->
                    tickerDao.updateDisplayOrder(ticker.symbol, index)
                }
                // Re-fetch and swap
                val updated1 = tickerDao.getTickerBySymbol(symbol1)
                val updated2 = tickerDao.getTickerBySymbol(symbol2)
                if (updated1 != null && updated2 != null) {
                    tickerDao.updateDisplayOrder(symbol1, updated2.displayOrder)
                    tickerDao.updateDisplayOrder(symbol2, updated1.displayOrder)
                }
            } else {
                tickerDao.updateDisplayOrder(symbol1, order2)
                tickerDao.updateDisplayOrder(symbol2, order1)
            }
        }
    }

    override suspend fun updateWatchlistOrder(orderedSymbols: List<String>) = withContext(Dispatchers.IO) {
        orderedSymbols.forEachIndexed { index, symbol ->
            tickerDao.updateDisplayOrder(symbol, index)
        }
        val firebaseManager = (context.applicationContext as? com.example.EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            val allEntities = tickerDao.getAllTickersNonFlow()
            allEntities.forEach { entity ->
                firebaseManager.syncWatchlistSingle(entity)
            }
        }
    }

    override fun getAllTrades(): Flow<List<TradeEntity>> {
        return tradeDao.getAllTrades().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTradesForTicker(symbol: String): Flow<List<TradeEntity>> {
        return tradeDao.getTradesForTicker(symbol).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun insertTrade(trade: TradeEntity) = withContext(Dispatchers.IO) {
        val entity = trade.toEntity()
        tradeDao.insertTrade(entity)

        // Sync with Firebase
        val firebaseManager = (context.applicationContext as? com.example.EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            firebaseManager.syncTradeSingle(entity)
        }
    }

    override suspend fun deleteTrade(id: Int) = withContext(Dispatchers.IO) {
        tradeDao.deleteTradeById(id)

        // Sync with Firebase
        val firebaseManager = (context.applicationContext as? com.example.EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            firebaseManager.removeTradeSingle(id)
        }
    }

    override suspend fun getCalculatorSnapshot(symbol: String): CalculatorSnapshot? = withContext(Dispatchers.IO) {
        calculatorSnapshotDao.getSnapshot(symbol)?.toDomain()
    }

    override fun getCalculatorSnapshotFlow(symbol: String): Flow<CalculatorSnapshot?> {
        return calculatorSnapshotDao.getSnapshotFlow(symbol).map { it?.toDomain() }
    }

    override fun getAllCalculatorSnapshotsFlow(): Flow<List<CalculatorSnapshot>> {
        return calculatorSnapshotDao.getAllSnapshotsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun saveCalculatorSnapshot(snapshot: CalculatorSnapshot) = withContext(Dispatchers.IO) {
        val entity = snapshot.toEntity()
        calculatorSnapshotDao.insertSnapshot(entity)

        // Sync with Firebase
        val firebaseManager = (context.applicationContext as? com.example.EquityIQApplication)?.container?.firebaseManager
        if (firebaseManager != null && firebaseManager.isReady()) {
            firebaseManager.syncSnapshotSingle(entity)
        }
    }

    // API Search for tickers
    override suspend fun searchTickers(query: String): List<FmpSearchResponse> = withContext(Dispatchers.IO) {
        val key = getApiKey()
        if (key.isEmpty()) return@withContext emptyList()
        try {
            throttleFinnhubCall()
            val response = apiService.searchSymbols(query = query, apiKey = key)
            response.result?.map {
                FmpSearchResponse(
                    symbol = it.symbol ?: it.displaySymbol ?: "",
                    name = it.description,
                    currency = null,
                    stockExchange = it.type
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchBaselineWithGemini(symbol: String, priceContextVal: Double = 0.0): GeminiStockBaseline? = withContext(Dispatchers.IO) {
        val apiKey = getGeminiApiKey()
        if (apiKey.isEmpty()) {
            return@withContext null
        }
        val tickerCurrency = com.example.domain.util.getTickerCurrency(symbol).ifEmpty { "USD" }
        val priceContextPrompt = if (priceContextVal > 0.0) {
            "The real-time market price of '$symbol' is exactly $priceContextVal $tickerCurrency (from real-time quote feed). Base all downstream ratios and per-share calculations consistently on this live market price."
        } else {
            "Utilize the latest real-time stock price of '$symbol' on Yahoo Finance in $tickerCurrency for consistent ratios."
        }
        val prompt = """
            You are a strict financial data extractor. You must ONLY extract corporate financial data from the Financial Statements tab on yahoofinance.com for ticker symbol '$symbol' (specifically: https://finance.yahoo.com/quote/$symbol/financials, /balance-sheet, and /cash-flow).
            $priceContextPrompt
            
            SOURCE OF TRUTH RULES:
            1. Use ONLY the 'Trailing Twelve Months (TTM)' column from the Financial Statements tab on yahoofinance.com.
            2. Never use any other website, summary estimations, or ungrounded training memory.
            3. Free Cash Flow MUST be parsed directly from the Cash Flow tab as Operating Cash Flow (TTM) minus Capital Expenditures (TTM).
            4. Balance Sheet items (Cash & Short-Term Investments, Total Long-Term Debt, Common Stock Shares Outstanding) must come strictly from the latest quarterly balance sheet on the Balance Sheet tab.
            5. TTM Revenue and TTM Net Income must come strictly from the Income Statement tab under the TTM column.
            
            Specifically extract and calculate:
            1. TTM Revenue (total revenue for trailing twelve months, in full raw number e.g. 385250000000)
            2. TTM Free Cash Flow (Operating Cash Flow minus Capital Expenditure under TTM column, in full raw number e.g. 104000000000)
            3. FCF growth (estimated annual growth rate for the next 5-10 years, in raw percentage e.g. 8.5)
            4. Historical FCF yield (average FCF yield over historical years, in raw percentage e.g. 4.3)
            5. Cash on hands (total cash and cash equivalents/short-term investments, in full raw number e.g. 130000000000)
            6. LT debt (total long-term debt, in full raw number e.g. 95000000000)
            7. ROIC (Return on Invested Capital, in raw percentage e.g. 24.5)
            8. TTM Net Income (total net income for trailing twelve months, in full raw number e.g. 97000000000)
            9. Common Shares Outstanding (latest reported shares count, in full raw number e.g. 15400000000)
            10. Interest Coverage (Operating Income / EBIT divided by Interest Expense under TTM column on Income Statement, in raw number e.g. 18.5)
            
            Return a valid JSON object matching the following structure ONLY. No markdown ticks, just pure JSON:
            {
              "companyName": "string",
              "currentPrice": number,
              "marketCap": number,
              "sharesOutstanding": number,
              "revenuePerShare": number,
              "fcfPerShare": number,
              "fcfMarginPercent": number,
              "netCashPerShare": number,
              "roicPercent": number,
              "fcfGrowthRate": number,
              "ttmRevenue": number,
              "ttmFcf": number,
              "historicalFcfYield": number,
              "cashOnHand": number,
              "ltDebt": number,
              "interestCoverage": number,
              "ttmNetIncome": number
            }
            CRITICAL FORMATTING INSTRUCTIONS:
            - All numeric fields MUST be pure raw numbers without quotes, %, commas, or abbreviations (M, B, K).
            - For marketCap, pass the full number (e.g. 150000000000).
            - For rates/percentages (fcfGrowthRate, roicPercent, historicalFcfYield, fcfMarginPercent), pass them as raw percentage numbers (e.g. 8.5 for 8.5%).
        """.trimIndent()
        
        val requestWithSearch = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.0),
            tools = listOf(
                GeminiTool(
                    googleSearch = GeminiGoogleSearch(),
                    googleSearchRetrieval = GeminiGoogleSearch()
                )
            )
        )

        try {
            var response: GeminiResponse? = null
            var lastError: Exception? = null
            // Primary recommended model gemini-3.5-flash with fast fallback to gemini-3.1-flash-lite-preview
            val models = listOf(
                "gemini-3.5-flash",
                "gemini-3.1-flash-lite-preview",
                "gemini-flash-latest"
            )

            for (model in models) {
                try {
                    val res = geminiApiService.generateContent(model, apiKey, requestWithSearch)
                    if (res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text != null) {
                        response = res
                        break
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (e is retrofit2.HttpException) {
                        val code = e.code()
                        if (code == 429) {
                            // Record cooldown for rate limits
                            lastGeminiSyncMap[symbol.uppercase().trim()] = System.currentTimeMillis()
                            delay(1500L)
                        } else if (code == 404) {
                            continue
                        }
                    }
                }
            }

            if (response == null) {
                if (lastError != null) {
                    if (lastError is retrofit2.HttpException) {
                        val code = lastError.code()
                        if (code == 429) {
                            lastGeminiSyncMap[symbol.uppercase().trim()] = System.currentTimeMillis()
                            throw Exception("Gemini Free Tier rate limit reached (HTTP 429). Please wait ~60s before retrying or verify your Google Gemini API key.")
                        } else if (code == 404) {
                            throw Exception("Gemini model endpoint not found (HTTP 404). Please verify your Google Gemini API Key.")
                        } else if (code == 400 || code == 403) {
                            throw Exception("Invalid Gemini API Key or permission denied (HTTP $code). Please verify your API key in Settings.")
                        }
                    }
                    throw lastError
                }
                return@withContext null
            }

            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (text != null) {
                var clean = text.trim()
                if (clean.startsWith("```json")) {
                    clean = clean.removePrefix("```json")
                } else if (clean.startsWith("```")) {
                    clean = clean.removePrefix("```")
                }
                if (clean.endsWith("```")) {
                    clean = clean.removeSuffix("```")
                }
                clean = clean.trim()
                val firstBrace = clean.indexOf('{')
                val lastBrace = clean.lastIndexOf('}')
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    clean = clean.substring(firstBrace, lastBrace + 1)
                }
                moshi.adapter(GeminiStockBaseline::class.java).fromJson(clean)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // SYNC LIVE ticker data & create/update snapshot with calculated fields
    override suspend fun syncTickerData(symbol: String, force: Boolean): CalculatorSnapshot = withContext(Dispatchers.IO) {
        val sym = symbol.uppercase().trim()
        val now = System.currentTimeMillis()
        val existing = calculatorSnapshotDao.getSnapshot(sym)
        val cachedTicker = tickerDao.getTickerBySymbol(sym)

        // Cache Freshness Check (5-minute TTL): skip network call if fresh snapshot exists within 5-minute window and not forced
        val lastSyncTime = lastRestSyncMap[sym] ?: existing?.lastFetched ?: 0L
        if (!force && (now - lastSyncTime) < CACHE_TTL_MS) {
            if (existing != null && existing.currentPrice > 0.0 && (cachedTicker?.livePrice ?: 0.0) > 0.0) {
                return@withContext existing.toDomain()
            }
        }

        var livePrice = 0.0
        var changePercent: Double? = cachedTicker?.changePercent
        var companyName = sym
        var hitRateLimit = false

        // Check if we already have a valid local cache of the company name to avoid redundant network lookups
        val hasValidCachedName = cachedTicker != null && cachedTicker.companyName.isNotEmpty() && cachedTicker.companyName != sym
        if (hasValidCachedName) {
            companyName = cachedTicker!!.companyName
        }

        // Try Yahoo Finance quote first (vital for Canadian stocks like AW.TO and universally reliable for real-time prices)
        val yahooQuote = fetchLiveQuoteFromYahoo(sym)
        if (yahooQuote != null && yahooQuote.first > 0.0) {
            livePrice = yahooQuote.first
            if (yahooQuote.second != null) {
                changePercent = yahooQuote.second
            }
            if (!yahooQuote.third.isNullOrEmpty() && !hasValidCachedName) {
                companyName = yahooQuote.third!!
            }
        }

        val apiKey = getApiKey()
        if (livePrice <= 0.0 && apiKey.isNotEmpty()) {
            try {
                throttleFinnhubCall()
                val quote = apiService.getQuote(sym, apiKey)
                livePrice = quote.price ?: 0.0
                if (quote.percentChange != null) {
                    changePercent = quote.percentChange
                } else if (quote.previousClose != null && quote.previousClose > 0.0 && livePrice > 0.0) {
                    changePercent = ((livePrice - quote.previousClose) / quote.previousClose) * 100.0
                }
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 429) {
                    hitRateLimit = true
                    lastRestSyncMap[sym] = now
                }
            }
        }

        if (!hasValidCachedName && !hitRateLimit && apiKey.isNotEmpty() && companyName == sym) {
            try {
                throttleFinnhubCall()
                val profile = apiService.getProfile(sym, apiKey)
                if (!profile.companyName.isNullOrEmpty()) {
                    companyName = profile.companyName
                }
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 429) {
                    hitRateLimit = true
                    lastRestSyncMap[sym] = now
                }
            }
        }

        // If rate limited or price is 0.0 and we have Gemini key, try fallback
        if (livePrice <= 0.0 && getGeminiApiKey().isNotEmpty()) {
            try {
                return@withContext forceGeminiSync(sym, force = force)
            } catch (e: Exception) {
                // Ignore fallback error
            }
        }

        // If rate limited and we have an existing local snapshot, gracefully return existing data without breaking the UI
        if (hitRateLimit && (livePrice <= 0.0)) {
            if (existing != null && existing.currentPrice > 0.0) {
                return@withContext existing.toDomain()
            } else if (cachedTicker != null && cachedTicker.livePrice > 0.0) {
                livePrice = cachedTicker.livePrice
            } else {
                throw Exception("Finnhub Free API limit reached (30 requests/min). Please wait ~30-60s before refreshing.")
            }
        }
        
        // Load existing snapshot to preserve user settings if any, else create standard default
        val finalPrice = if (livePrice > 0.0) livePrice else (existing?.currentPrice ?: 0.0)

        // Finnhub is STRICTLY for getquote and getprofile. Do NOT query Finnhub keyMetrics or populate variables with Finnhub metrics.
        val sharesOutstanding = existing?.sharesOutstanding ?: 1.0
        val mcap = if (finalPrice > 0.0 && sharesOutstanding > 1.0) finalPrice * sharesOutstanding else (existing?.marketCap ?: 0.0)
        val fcfPerShare = existing?.fcfPerShare ?: 0.0
        val salesPerShare = existing?.revenuePerShare ?: 0.0
        val fcfMargin = existing?.fcfMarginPercent ?: 10.0
        
        val snapshotEntity = CalculatorSnapshotEntity(
            symbol = sym,
            currentPrice = finalPrice,
            fcfPerShare = fcfPerShare,
            revenuePerShare = salesPerShare,
            fcfMarginPercent = fcfMargin,
            roicPercent = existing?.roicPercent ?: 0.0,
            netCashPerShare = existing?.netCashPerShare ?: 0.0,
            sharesOutstanding = sharesOutstanding,
            marketCap = mcap,
            fcfGrowthRate = existing?.fcfGrowthRate ?: 8.0,
            equityGrowthRate = existing?.equityGrowthRate ?: 8.0,
            fundamentalGrowthRate = existing?.fundamentalGrowthRate ?: 2.5,
            historicalFcfMargin = existing?.historicalFcfMargin ?: fcfMargin,
            riskFreeRate = existing?.riskFreeRate ?: getRiskFreeRate().toDouble(),
            riskPremium = existing?.riskPremium ?: getRiskPremium().toDouble(),
            terminalGrowthRate = existing?.terminalGrowthRate ?: 2.5,
            highGrowthYears = existing?.highGrowthYears ?: 5,
            historicalFcfYield = existing?.historicalFcfYield ?: (if (finalPrice > 0.0) (fcfPerShare / finalPrice) * 100.0 else 0.0),
            lastFetched = System.currentTimeMillis(),
            ttmRevenue = existing?.ttmRevenue ?: 0.0,
            ttmFcf = existing?.ttmFcf ?: 0.0,
            cashOnHand = existing?.cashOnHand ?: 0.0,
            ltDebt = existing?.ltDebt ?: 0.0,
            interestCoverage = existing?.interestCoverage ?: 0.0,
            ttmNetIncome = existing?.ttmNetIncome ?: 0.0,
            checkedQualitativeTitles = existing?.checkedQualitativeTitles ?: ""
        )

        // Save elements
        val currentTicker = tickerDao.getTickerBySymbol(sym) ?: cachedTicker
        if (currentTicker != null) {
            tickerDao.updatePrice(sym, finalPrice, companyName, System.currentTimeMillis(), currentTicker.logoUrl, changePercent)
        } else {
            val maxOrder = tickerDao.getMaxDisplayOrder() ?: 0
            tickerDao.insert(
                WatchlistTickerEntity(
                    symbol = sym,
                    companyName = companyName,
                    livePrice = finalPrice,
                    lastFetched = System.currentTimeMillis(),
                    displayOrder = maxOrder + 1,
                    changePercent = changePercent
                )
            )
        }
        calculatorSnapshotDao.insertSnapshot(snapshotEntity)

        lastRestSyncMap[sym] = System.currentTimeMillis()

        snapshotEntity.toDomain()
    }

    // Force synchronize everything (baseline + valuation growth) directly using Gemini API.
    override suspend fun forceGeminiSync(symbol: String, force: Boolean): CalculatorSnapshot = withContext(Dispatchers.IO) {
        val sym = symbol.uppercase().trim()
        val now = System.currentTimeMillis()
        val existing = calculatorSnapshotDao.getSnapshot(sym)

        // Cache Freshness Check (5-minute TTL): skip heavy LLM extraction if fresh baseline snapshot exists within 5-minute window and not forced
        val lastSyncTime = lastGeminiSyncMap[sym] ?: existing?.lastFetched ?: 0L
        if (!force && (now - lastSyncTime) < CACHE_TTL_MS) {
            if (existing != null && existing.ttmRevenue > 0.0) {
                return@withContext existing.toDomain()
            }
        }

        val geminiKey = getGeminiApiKey()
        if (geminiKey.isEmpty()) {
            throw Exception("Google Gemini API Key is missing in Settings. Please set it to enable AI-Powered Data Entry.")
        }

        val existingPrice = existing?.currentPrice ?: 0.0
        val cachedTicker = tickerDao.getTickerBySymbol(sym)
        
        // Real-time minute-by-minute pricing from Yahoo Finance / Finnhub
        val apiKey = getApiKey()
        var liveQuotePrice = 0.0
        var changePercent: Double? = cachedTicker?.changePercent
        var yahooName: String? = null

        val yahooQuote = fetchLiveQuoteFromYahoo(sym)
        if (yahooQuote != null && yahooQuote.first > 0.0) {
            liveQuotePrice = yahooQuote.first
            if (yahooQuote.second != null) {
                changePercent = yahooQuote.second
            }
            if (!yahooQuote.third.isNullOrEmpty()) {
                yahooName = yahooQuote.third
            }
        }

        if (liveQuotePrice <= 0.0 && apiKey.isNotEmpty()) {
            try {
                throttleFinnhubCall()
                val quote = apiService.getQuote(sym, apiKey)
                liveQuotePrice = quote.price ?: 0.0
                if (quote.percentChange != null) {
                    changePercent = quote.percentChange
                } else if (quote.previousClose != null && quote.previousClose > 0.0 && liveQuotePrice > 0.0) {
                    changePercent = ((liveQuotePrice - quote.previousClose) / quote.previousClose) * 100.0
                }
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 429) {
                    lastRestSyncMap[sym] = now
                }
            }
        }
        val priceForContext = if (liveQuotePrice > 0.0) liveQuotePrice else existingPrice

        // Fetch strictly using Yahoo Finance Financial Statements as source of truth
        val geminiBaseline = fetchBaselineWithGemini(sym, priceContextVal = priceForContext) 
            ?: throw Exception("Failed to fetch verified financial statement data from Yahoo Finance via Gemini Grounding. Please check your internet connection or API key.")

        val livePrice = when {
            liveQuotePrice > 0.0 -> liveQuotePrice
            existingPrice > 0.0 -> existingPrice
            else -> geminiBaseline.currentPrice ?: 0.0
        }

        val companyName = when {
            !geminiBaseline.companyName.isNullOrEmpty() && geminiBaseline.companyName != sym -> geminiBaseline.companyName
            !yahooName.isNullOrEmpty() -> yahooName
            cachedTicker != null && cachedTicker.companyName.isNotEmpty() && cachedTicker.companyName != sym -> cachedTicker.companyName
            else -> sym
        }
        
        // Sanitize Market Cap from Gemini (handle Billions/Millions scaling errors)
        var mcap = geminiBaseline.marketCap ?: 0.0
        if (mcap > 0.0 && mcap < 100000.0) {
            mcap *= 1_000_000_000.0
        } else if (mcap >= 100000.0 && mcap < 100000000.0) {
            mcap *= 1_000_000.0
        }

        if (mcap <= 0.0 && geminiBaseline.sharesOutstanding != null && geminiBaseline.sharesOutstanding > 0.0 && livePrice > 0.0) {
            var rawShares = geminiBaseline.sharesOutstanding
            if (rawShares < 1000.0) {
                rawShares *= 1_000_000_000.0
            } else if (rawShares < 1000000.0) {
                rawShares *= 1_000_000.0
            }
            mcap = livePrice * rawShares
        }

        // CONSISTENT outstanding shares calculation
        val sharesOutstanding = if (livePrice > 0.0) mcap / livePrice else 1.0
        val safeShares = if (sharesOutstanding > 1.0) sharesOutstanding else (geminiBaseline.sharesOutstanding ?: 1.0)

        // Sanitize Revenue & Free Cash Flow from Gemini
        var ttmRevenue = geminiBaseline.ttmRevenue ?: 0.0
        if (ttmRevenue > 0.0) {
            if (ttmRevenue < 100000.0) {
                ttmRevenue *= 1_000_000_000.0
            } else if (ttmRevenue < 100000000.0) {
                ttmRevenue *= 1_000_000.0
            }
        }

        var ttmFcf = geminiBaseline.ttmFcf ?: 0.0
        if (ttmFcf > 0.0) {
            if (ttmFcf < 50000.0) {
                ttmFcf *= 1_000_000_000.0
            } else if (ttmFcf < 50000000.0) {
                ttmFcf *= 1_000_000.0
            }
        }

        val fcfPerShare = if (geminiBaseline.fcfPerShare != null && geminiBaseline.fcfPerShare > 0.0) {
            geminiBaseline.fcfPerShare
        } else if (ttmFcf > 0.0 && safeShares > 0.0) {
            ttmFcf / safeShares
        } else {
            0.0
        }

        val revPerShare = if (geminiBaseline.revenuePerShare != null && geminiBaseline.revenuePerShare > 0.0) {
            geminiBaseline.revenuePerShare
        } else if (ttmRevenue > 0.0 && safeShares > 0.0) {
            ttmRevenue / safeShares
        } else {
            0.0
        }

        val finalTtmRevenue = if (ttmRevenue > 0.0) ttmRevenue else revPerShare * safeShares
        val finalTtmFcf = if (ttmFcf > 0.0) ttmFcf else fcfPerShare * safeShares

        var cashOnHand = geminiBaseline.cashOnHand ?: 0.0
        if (cashOnHand > 0.0) {
            if (cashOnHand < 50000.0) {
                cashOnHand *= 1_000_000_000.0
            } else if (cashOnHand < 50000000.0) {
                cashOnHand *= 1_000_000.0
            }
        }

        var ltDebt = geminiBaseline.ltDebt ?: 0.0
        if (ltDebt > 0.0) {
            if (ltDebt < 50000.0) {
                ltDebt *= 1_000_000_000.0
            } else if (ltDebt < 50000000.0) {
                ltDebt *= 1_000_000.0
            }
        }

        var ttmNetIncome = geminiBaseline.ttmNetIncome ?: 0.0
        val absNetIncome = Math.abs(ttmNetIncome)
        if (absNetIncome > 0.0) {
            if (absNetIncome < 50000.0) {
                ttmNetIncome *= 1_000_000_000.0
            } else if (absNetIncome < 50000000.0) {
                ttmNetIncome *= 1_000_000.0
            }
        }

        val calculatedNetCashPerShare = if (safeShares > 0.0) (cashOnHand - ltDebt) / safeShares else 0.0
        val fcfMargin = geminiBaseline.fcfMarginPercent ?: (if (finalTtmRevenue > 0.0) (finalTtmFcf / finalTtmRevenue) * 100.0 else 10.0)
        
        val netCashPerShare = if (geminiBaseline.netCashPerShare != null && Math.abs(geminiBaseline.netCashPerShare) > 0.01) {
            if (cashOnHand > 0.0 || ltDebt > 0.0) calculatedNetCashPerShare else geminiBaseline.netCashPerShare
        } else {
            calculatedNetCashPerShare
        }

        val roic = geminiBaseline.roicPercent ?: 0.0
        val interestCoverage = geminiBaseline.interestCoverage ?: (existing?.interestCoverage ?: 0.0)
        val fcfGrowth = geminiBaseline.fcfGrowthRate ?: 8.0
        val aiHistFcfYield = geminiBaseline.historicalFcfYield ?: 0.0
        val finalHistFcfYield = if (aiHistFcfYield > 0.0) aiHistFcfYield else (if (livePrice > 0.0) (fcfPerShare / livePrice) * 100.0 else 0.0)

        val snapshotEntity = CalculatorSnapshotEntity(
            symbol = sym,
            currentPrice = livePrice,
            fcfPerShare = fcfPerShare,
            revenuePerShare = revPerShare,
            fcfMarginPercent = fcfMargin,
            roicPercent = roic,
            netCashPerShare = netCashPerShare,
            sharesOutstanding = safeShares,
            marketCap = mcap,
            fcfGrowthRate = fcfGrowth,
            equityGrowthRate = fcfGrowth,
            fundamentalGrowthRate = 2.5,
            historicalFcfMargin = if (revPerShare > 0.0) (fcfPerShare / revPerShare) * 100.0 else fcfMargin,
            riskFreeRate = getRiskFreeRate().toDouble(),
            riskPremium = getRiskPremium().toDouble(),
            terminalGrowthRate = 2.5,
            highGrowthYears = 5,
            historicalFcfYield = finalHistFcfYield,
            lastFetched = System.currentTimeMillis(),
            ttmRevenue = finalTtmRevenue,
            ttmFcf = finalTtmFcf,
            cashOnHand = cashOnHand,
            ltDebt = ltDebt,
            interestCoverage = interestCoverage,
            ttmNetIncome = ttmNetIncome,
            checkedQualitativeTitles = existing?.checkedQualitativeTitles ?: ""
        )

        // Save elements
        val currentTicker = tickerDao.getTickerBySymbol(sym) ?: cachedTicker
        if (currentTicker != null) {
            tickerDao.updatePrice(sym, livePrice, companyName, System.currentTimeMillis(), currentTicker.logoUrl, changePercent)
        } else {
            val maxOrder = tickerDao.getMaxDisplayOrder() ?: 0
            tickerDao.insert(
                WatchlistTickerEntity(
                    symbol = sym,
                    companyName = companyName,
                    livePrice = livePrice,
                    lastFetched = System.currentTimeMillis(),
                    displayOrder = maxOrder + 1,
                    changePercent = changePercent
                )
            )
        }
        calculatorSnapshotDao.insertSnapshot(snapshotEntity)

        lastGeminiSyncMap[sym] = System.currentTimeMillis()

        snapshotEntity.toDomain()
    }

    // Key Validation & Diagnostic Helpers
    override suspend fun testFinnhubConnection(key: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(Exception("API Key is empty"))
        }
        try {
            throttleFinnhubCall()
            val quote = apiService.getQuote("AAPL", cleanKey)
            if (quote.price != null && quote.price > 0.0) {
                Result.success("Connected! Finnhub live quote for AAPL: $${String.format(java.util.Locale.US, "%.2f", quote.price)}")
            } else {
                Result.failure(Exception("Received invalid response from Finnhub. Please verify the key."))
            }
        } catch (e: Exception) {
            if (e is retrofit2.HttpException) {
                when (e.code()) {
                    401, 403 -> Result.failure(Exception("Invalid Finnhub API Key (HTTP ${e.code()}). Please check your key."))
                    429 -> Result.failure(Exception("Finnhub Free Limit Reached (HTTP 429). Key is recognized, but quota is active. Wait 60s."))
                    else -> Result.failure(Exception("Finnhub server returned HTTP ${e.code()}"))
                }
            } else {
                Result.failure(Exception(e.message ?: "Connection failed"))
            }
        }
    }

    override suspend fun testGeminiConnection(key: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(Exception("Gemini API Key is empty"))
        }
        try {
            val req = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Respond with 'OK'")))),
                generationConfig = GeminiGenerationConfig(temperature = 0.0)
            )
            val res = geminiApiService.generateContent("gemini-3.5-flash", cleanKey, req)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                Result.success("Connected! Google Gemini AI is active and responding.")
            } else {
                Result.failure(Exception("Gemini returned empty response"))
            }
        } catch (e: Exception) {
            if (e is retrofit2.HttpException) {
                when (e.code()) {
                    400, 403 -> Result.failure(Exception("Invalid Gemini API Key (HTTP ${e.code()}). Please check your key."))
                    429 -> Result.failure(Exception("Gemini Rate Limit (HTTP 429). Key is recognized, but quota limit is active."))
                    404 -> Result.failure(Exception("Gemini Model Endpoint (HTTP 404)."))
                    else -> Result.failure(Exception("Gemini server returned HTTP ${e.code()}"))
                }
            } else {
                Result.failure(Exception(e.message ?: "Gemini connection failed"))
            }
        }
    }
}

