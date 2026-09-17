package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.CryptoManager
import com.example.data.local.AppDatabase
import com.example.data.local.CalculatorSnapshotEntity
import com.example.data.local.TradeLogEntity
import com.example.data.local.WatchlistTickerEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseManager(private val context: Context) {

    private val cryptoManager = CryptoManager(context)
    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isInitialized = MutableStateFlow(false)
    val authState = MutableStateFlow<FirebaseAuthState>(FirebaseAuthState.SignedOut)
    val syncState = MutableStateFlow<FirebaseSyncState>(FirebaseSyncState.Idle)

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private val listenerRegistrations = mutableListOf<ListenerRegistration>()

    init {
        trySetupFirebase()
    }

    fun trySetupFirebase(): Boolean {
        if (isInitialized.value) return true

        try {
            // Check if Firebase is already initialized via google-services.json
            val apps = FirebaseApp.getApps(context)
            val app = if (apps.isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                // Try initializing programmatically with custom or preset options
                val apiKey = getFirebaseApiKey()
                val projectId = getFirebaseProjectId()
                val appId = getFirebaseAppId()

                if (apiKey.isNotEmpty() && projectId.isNotEmpty() && appId.isNotEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setProjectId(projectId)
                        .setApplicationId(appId)
                        .build()
                    FirebaseApp.initializeApp(context, options)
                } else {
                    null
                }
            }

            if (app != null) {
                auth = FirebaseAuth.getInstance()
                firestore = FirebaseFirestore.getInstance()
                isInitialized.value = true

                // Setup auth listener
                auth?.addAuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        authState.value = FirebaseAuthState.SignedIn(
                            uid = user.uid,
                            email = user.email ?: "",
                            displayName = user.displayName ?: ""
                        )
                        startRealtimeSync(user.uid)
                    } else {
                        authState.value = FirebaseAuthState.SignedOut
                        stopRealtimeSync()
                    }
                }

                val initialUser = auth?.currentUser
                if (initialUser != null) {
                    startRealtimeSync(initialUser.uid)
                }

                Log.d("FirebaseManager", "Firebase successfully initialized.")
                return true
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error initializing Firebase: ${e.message}", e)
        }
        return false
    }

    private fun getStringResourceByName(name: String): String {
        return try {
            val resId = context.resources.getIdentifier(name, "string", context.packageName)
            if (resId != 0) context.getString(resId) else ""
        } catch (e: Exception) {
            ""
        }
    }

    // Settings Configuration
    fun getFirebaseApiKey(): String {
        val resVal = getStringResourceByName("google_api_key")
        if (resVal.isNotEmpty()) return resVal
        val key = BuildConfig.FIREBASE_API_KEY
        if (key.isNotEmpty() && key != "YOUR_FIREBASE_API_KEY_HERE") return key
        return cryptoManager.getFirebaseApiKey()
    }

    fun getFirebaseProjectId(): String {
        val resVal = getStringResourceByName("project_id")
        if (resVal.isNotEmpty()) return resVal
        val projId = BuildConfig.FIREBASE_PROJECT_ID
        if (projId.isNotEmpty() && projId != "YOUR_FIREBASE_PROJECT_ID_HERE") return projId
        return cryptoManager.getFirebaseProjectId()
    }

    fun getFirebaseAppId(): String {
        val resVal = getStringResourceByName("google_app_id")
        if (resVal.isNotEmpty()) return resVal
        val appId = BuildConfig.FIREBASE_APP_ID
        if (appId.isNotEmpty() && appId != "YOUR_FIREBASE_APP_ID_HERE") return appId
        return cryptoManager.getFirebaseAppId()
    }

    fun getFirebaseAuthClientId(): String {
        val resVal = getStringResourceByName("default_web_client_id")
        if (resVal.isNotEmpty()) return resVal
        val clientId = BuildConfig.FIREBASE_CLIENT_ID
        if (clientId.isNotEmpty() && clientId != "YOUR_FIREBASE_CLIENT_ID_HERE") return clientId
        return cryptoManager.getFirebaseAuthClientId()
    }

    fun saveFirebaseConfig(apiKey: String, projectId: String, appId: String, clientId: String) {
        cryptoManager.saveFirebaseConfig(apiKey, projectId, appId, clientId)
        trySetupFirebase()
    }

    fun isReady(): Boolean {
        return isInitialized.value && auth != null && firestore != null
    }

    fun getUserId(): String? {
        return auth?.currentUser?.uid
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isReady()) {
            return@withContext Result.failure(Exception("Firebase not initialized. Please configure settings first."))
        }
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth!!.signInWithCredential(credential).await()
            val user = authResult.user
            if (user != null) {
                val userState = FirebaseAuthState.SignedIn(
                    uid = user.uid,
                    email = user.email ?: "",
                    displayName = user.displayName ?: ""
                )
                authState.value = userState
                startRealtimeSync(user.uid)
                syncDataAcrossDevices()
                Result.success(user.email ?: "Success")
            } else {
                Result.failure(Exception("Failed to obtain signed-in user profile from Google credentials."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        stopRealtimeSync()
        scope.launch {
            try {
                db.tickerDao().deleteAllTickers()
                db.tradeDao().deleteAllTrades()
                db.calculatorSnapshotDao().deleteAllSnapshots()
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error clearing local database on sign out", e)
            }
        }
        auth?.signOut()
        authState.value = FirebaseAuthState.SignedOut
    }

    private fun stopRealtimeSync() {
        synchronized(listenerRegistrations) {
            listenerRegistrations.forEach { it.remove() }
            listenerRegistrations.clear()
        }
    }

    private fun startRealtimeSync(uid: String) {
        if (!isReady()) return
        stopRealtimeSync()

        try {
            val userDoc = firestore!!.collection("users").document(uid)

            // 1. Real-time Watchlist listener
            val watchlistReg = userDoc.collection("watchlist")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FirebaseManager", "Watchlist real-time sync error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        scope.launch {
                            handleRemoteWatchlistSnapshot(uid, snapshot)
                        }
                    }
                }

            // 2. Real-time Trades listener
            val tradesReg = userDoc.collection("trades")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FirebaseManager", "Trades real-time sync error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        scope.launch {
                            handleRemoteTradesSnapshot(uid, snapshot)
                        }
                    }
                }

            // 3. Real-time Snapshots listener
            val snapshotsReg = userDoc.collection("snapshots")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FirebaseManager", "Snapshots real-time sync error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        scope.launch {
                            handleRemoteSnapshotsSnapshot(uid, snapshot)
                        }
                    }
                }

            // 4. Real-time Preferences listener
            val prefReg = userDoc.collection("preferences").document("settings")
                .addSnapshotListener { doc, error ->
                    if (error != null) {
                        Log.e("FirebaseManager", "Preferences real-time sync error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    if (doc != null && doc.exists()) {
                        scope.launch {
                            handleRemotePreferences(doc)
                        }
                    }
                }

            synchronized(listenerRegistrations) {
                listenerRegistrations.add(watchlistReg)
                listenerRegistrations.add(tradesReg)
                listenerRegistrations.add(snapshotsReg)
                listenerRegistrations.add(prefReg)
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Failed to register real-time Firestore listeners: ${e.message}", e)
        }
    }

    private suspend fun handleRemoteWatchlistSnapshot(uid: String, snapshot: QuerySnapshot) {
        val tickerDao = db.tickerDao()
        val localTickers = tickerDao.getAllTickersNonFlow()

        if (snapshot.isEmpty) {
            // First-time sync: if cloud is empty but local has items, upload local items
            if (localTickers.isNotEmpty()) {
                val watchlistRef = firestore!!.collection("users").document(uid).collection("watchlist")
                for (local in localTickers) {
                    val data = hashMapOf(
                        "symbol" to local.symbol,
                        "companyName" to local.companyName,
                        "livePrice" to local.livePrice,
                        "lastFetched" to local.lastFetched,
                        "manuallyEnteredCostBasis" to local.manuallyEnteredCostBasis,
                        "targetPrice" to local.targetPrice,
                        "logoUrl" to local.logoUrl,
                        "displayOrder" to local.displayOrder,
                        "changePercent" to local.changePercent
                    )
                    watchlistRef.document(local.symbol).set(data, SetOptions.merge())
                }
            }
            return
        }

        val remoteSymbols = snapshot.documents.map { it.id.uppercase().trim() }.toSet()

        // 1. Insert or update remote items locally
        for (remoteDoc in snapshot.documents) {
            val symbol = remoteDoc.id.uppercase().trim()
            val local = localTickers.find { it.symbol.equals(symbol, ignoreCase = true) }

            val companyName = remoteDoc.getString("companyName") ?: symbol
            val livePrice = remoteDoc.getDouble("livePrice") ?: 0.0
            val lastFetched = remoteDoc.getLong("lastFetched") ?: 0L
            val manuallyEnteredCostBasis = remoteDoc.getDouble("manuallyEnteredCostBasis")
            val targetPrice = remoteDoc.getDouble("targetPrice")
            val logoUrl = remoteDoc.getString("logoUrl")
            val displayOrder = remoteDoc.getLong("displayOrder")?.toInt() ?: 0
            val changePercent = remoteDoc.getDouble("changePercent")

            if (local == null) {
                val newEntity = WatchlistTickerEntity(
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
                tickerDao.insert(newEntity)
            } else {
                val priceToUse = if (livePrice > 0.0) livePrice else local.livePrice
                val timeToUse = if (lastFetched > 0L) lastFetched else local.lastFetched
                val nameToUse = if (companyName.isNotEmpty() && companyName != symbol) companyName else local.companyName
                val logoToUse = logoUrl ?: local.logoUrl
                val changeToUse = changePercent ?: local.changePercent

                if (local.manuallyEnteredCostBasis != manuallyEnteredCostBasis) {
                    tickerDao.updateCostBasis(symbol, manuallyEnteredCostBasis)
                }
                if (local.targetPrice != targetPrice) {
                    tickerDao.updateTargetPrice(symbol, targetPrice)
                }
                if (local.displayOrder != displayOrder) {
                    tickerDao.updateDisplayOrder(symbol, displayOrder)
                }
                if (local.livePrice != priceToUse || local.companyName != nameToUse || local.lastFetched != timeToUse || local.logoUrl != logoToUse || local.changePercent != changeToUse) {
                    tickerDao.updatePrice(symbol, priceToUse, nameToUse, timeToUse, logoToUse, changeToUse)
                }
            }
        }

        // 2. Remove local tickers deleted remotely
        for (local in localTickers) {
            if (!remoteSymbols.contains(local.symbol.uppercase().trim())) {
                tickerDao.delete(local.symbol)
            }
        }
    }

    private suspend fun handleRemoteTradesSnapshot(uid: String, snapshot: QuerySnapshot) {
        val tradeDao = db.tradeDao()
        val localTrades = tradeDao.getAllTradesNonFlow()

        if (snapshot.isEmpty) {
            if (localTrades.isNotEmpty()) {
                val tradesRef = firestore!!.collection("users").document(uid).collection("trades")
                for (local in localTrades) {
                    val data = hashMapOf(
                        "id" to local.id,
                        "ticker" to local.ticker,
                        "tradeType" to local.tradeType,
                        "date" to local.date,
                        "contracts" to local.contracts,
                        "strikePrice" to local.strikePrice,
                        "premiumPerShare" to local.premiumPerShare,
                        "expiryDate" to local.expiryDate,
                        "fees" to local.fees,
                        "netCreditDebit" to local.netCreditDebit,
                        "annualizedReturn" to local.annualizedReturn,
                        "fcfYield" to local.fcfYield,
                        "isClosed" to local.isClosed,
                        "closePremium" to local.closePremium,
                        "closeFees" to local.closeFees,
                        "closeDate" to local.closeDate,
                        "manualOutcome" to local.manualOutcome
                    )
                    tradesRef.document(local.id.toString()).set(data, SetOptions.merge())
                }
            }
            return
        }

        val remoteIds = snapshot.documents.mapNotNull { it.id.toIntOrNull() }.toSet()

        for (remoteDoc in snapshot.documents) {
            val remoteId = remoteDoc.id.toIntOrNull() ?: continue
            val entity = TradeLogEntity(
                id = remoteId,
                ticker = remoteDoc.getString("ticker") ?: "",
                tradeType = remoteDoc.getString("tradeType") ?: "",
                date = remoteDoc.getLong("date") ?: 0L,
                contracts = remoteDoc.getLong("contracts")?.toInt() ?: 1,
                strikePrice = remoteDoc.getDouble("strikePrice") ?: 0.0,
                premiumPerShare = remoteDoc.getDouble("premiumPerShare") ?: 0.0,
                expiryDate = remoteDoc.getLong("expiryDate"),
                fees = remoteDoc.getDouble("fees") ?: 0.0,
                netCreditDebit = remoteDoc.getDouble("netCreditDebit") ?: 0.0,
                annualizedReturn = remoteDoc.getDouble("annualizedReturn") ?: 0.0,
                fcfYield = remoteDoc.getDouble("fcfYield") ?: 0.0,
                isClosed = remoteDoc.getBoolean("isClosed") ?: false,
                closePremium = remoteDoc.getDouble("closePremium") ?: 0.0,
                closeFees = remoteDoc.getDouble("closeFees") ?: 0.0,
                closeDate = remoteDoc.getLong("closeDate"),
                manualOutcome = remoteDoc.getString("manualOutcome") ?: ""
            )
            tradeDao.insertTrade(entity)
        }

        for (local in localTrades) {
            if (!remoteIds.contains(local.id)) {
                tradeDao.deleteTradeById(local.id)
            }
        }
    }

    private suspend fun handleRemoteSnapshotsSnapshot(uid: String, snapshot: QuerySnapshot) {
        val snapshotDao = db.calculatorSnapshotDao()
        val localSnapshots = snapshotDao.getAllSnapshotsNonFlow()

        if (snapshot.isEmpty) {
            if (localSnapshots.isNotEmpty()) {
                val snapshotsRef = firestore!!.collection("users").document(uid).collection("snapshots")
                for (local in localSnapshots) {
                    val data = hashMapOf(
                        "symbol" to local.symbol,
                        "currentPrice" to local.currentPrice,
                        "fcfPerShare" to local.fcfPerShare,
                        "revenuePerShare" to local.revenuePerShare,
                        "fcfMarginPercent" to local.fcfMarginPercent,
                        "roicPercent" to local.roicPercent,
                        "netCashPerShare" to local.netCashPerShare,
                        "sharesOutstanding" to local.sharesOutstanding,
                        "marketCap" to local.marketCap,
                        "fcfGrowthRate" to local.fcfGrowthRate,
                        "equityGrowthRate" to local.equityGrowthRate,
                        "fundamentalGrowthRate" to local.fundamentalGrowthRate,
                        "historicalFcfMargin" to local.historicalFcfMargin,
                        "riskFreeRate" to local.riskFreeRate,
                        "riskPremium" to local.riskPremium,
                        "terminalGrowthRate" to local.terminalGrowthRate,
                        "highGrowthYears" to local.highGrowthYears,
                        "historicalFcfYield" to local.historicalFcfYield,
                        "lastFetched" to local.lastFetched,
                        "ttmRevenue" to local.ttmRevenue,
                        "ttmFcf" to local.ttmFcf,
                        "cashOnHand" to local.cashOnHand,
                        "ltDebt" to local.ltDebt,
                        "interestCoverage" to local.interestCoverage,
                        "ttmNetIncome" to local.ttmNetIncome,
                        "checkedQualitativeTitles" to local.checkedQualitativeTitles
                    )
                    snapshotsRef.document(local.symbol).set(data, SetOptions.merge())
                }
            }
            return
        }

        val remoteSymbols = snapshot.documents.map { it.id.uppercase().trim() }.toSet()

        for (remoteDoc in snapshot.documents) {
            val symbol = remoteDoc.id.uppercase().trim()
            val entity = CalculatorSnapshotEntity(
                symbol = symbol,
                currentPrice = remoteDoc.getDouble("currentPrice") ?: 0.0,
                fcfPerShare = remoteDoc.getDouble("fcfPerShare") ?: 0.0,
                revenuePerShare = remoteDoc.getDouble("revenuePerShare") ?: 0.0,
                fcfMarginPercent = remoteDoc.getDouble("fcfMarginPercent") ?: 0.0,
                roicPercent = remoteDoc.getDouble("roicPercent") ?: 0.0,
                netCashPerShare = remoteDoc.getDouble("netCashPerShare") ?: 0.0,
                sharesOutstanding = remoteDoc.getDouble("sharesOutstanding") ?: 0.0,
                marketCap = remoteDoc.getDouble("marketCap") ?: 0.0,
                fcfGrowthRate = remoteDoc.getDouble("fcfGrowthRate") ?: 0.0,
                equityGrowthRate = remoteDoc.getDouble("equityGrowthRate") ?: 0.0,
                fundamentalGrowthRate = remoteDoc.getDouble("fundamentalGrowthRate") ?: 0.0,
                historicalFcfMargin = remoteDoc.getDouble("historicalFcfMargin") ?: 0.0,
                riskFreeRate = remoteDoc.getDouble("riskFreeRate") ?: 0.0,
                riskPremium = remoteDoc.getDouble("riskPremium") ?: 0.0,
                terminalGrowthRate = remoteDoc.getDouble("terminalGrowthRate") ?: 0.0,
                highGrowthYears = remoteDoc.getLong("highGrowthYears")?.toInt() ?: 10,
                historicalFcfYield = remoteDoc.getDouble("historicalFcfYield") ?: 0.0,
                lastFetched = remoteDoc.getLong("lastFetched") ?: 0L,
                ttmRevenue = remoteDoc.getDouble("ttmRevenue") ?: 0.0,
                ttmFcf = remoteDoc.getDouble("ttmFcf") ?: 0.0,
                cashOnHand = remoteDoc.getDouble("cashOnHand") ?: 0.0,
                ltDebt = remoteDoc.getDouble("ltDebt") ?: 0.0,
                interestCoverage = remoteDoc.getDouble("interestCoverage") ?: 0.0,
                ttmNetIncome = remoteDoc.getDouble("ttmNetIncome") ?: 0.0,
                checkedQualitativeTitles = remoteDoc.getString("checkedQualitativeTitles") ?: ""
            )
            snapshotDao.insertSnapshot(entity)
        }

        for (local in localSnapshots) {
            if (!remoteSymbols.contains(local.symbol.uppercase().trim())) {
                snapshotDao.deleteSnapshot(local.symbol)
            }
        }
    }

    private fun handleRemotePreferences(remoteDoc: DocumentSnapshot) {
        val remoteCurrency = remoteDoc.getString("defaultCurrency")
        val remoteRiskFree = remoteDoc.getDouble("riskFreeRate")?.toFloat()
        val remoteRiskPremium = remoteDoc.getDouble("riskPremium")?.toFloat()
        val remoteApiKey = remoteDoc.getString("fmpApiKey")
        val remoteGeminiApiKey = remoteDoc.getString("geminiApiKey")

        if (remoteCurrency != null && remoteCurrency != cryptoManager.getCurrency()) {
            cryptoManager.saveCurrency(remoteCurrency)
        }
        if (remoteRiskFree != null && remoteRiskFree != cryptoManager.getRiskFreeRate()) {
            cryptoManager.saveRiskFreeRate(remoteRiskFree)
        }
        if (remoteRiskPremium != null && remoteRiskPremium != cryptoManager.getRiskPremium()) {
            cryptoManager.saveRiskPremium(remoteRiskPremium)
        }
        if (!remoteApiKey.isNullOrEmpty() && cryptoManager.getApiKey().isEmpty()) {
            cryptoManager.saveApiKey(remoteApiKey)
        }
        if (!remoteGeminiApiKey.isNullOrEmpty() && cryptoManager.getGeminiApiKey().isEmpty()) {
            cryptoManager.saveGeminiApiKey(remoteGeminiApiKey)
        }
    }

    // Manual Bidirectional Cloud Sync
    suspend fun syncDataAcrossDevices(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isReady() || auth?.currentUser == null) {
            return@withContext Result.failure(Exception("User is not signed in or Firebase is not ready."))
        }

        val uid = auth!!.currentUser!!.uid
        syncState.value = FirebaseSyncState.Syncing

        try {
            val watchlistSnapshot = firestore!!.collection("users").document(uid).collection("watchlist").get().await()
            handleRemoteWatchlistSnapshot(uid, watchlistSnapshot)

            val tradesSnapshot = firestore!!.collection("users").document(uid).collection("trades").get().await()
            handleRemoteTradesSnapshot(uid, tradesSnapshot)

            val snapshotsSnapshot = firestore!!.collection("users").document(uid).collection("snapshots").get().await()
            handleRemoteSnapshotsSnapshot(uid, snapshotsSnapshot)

            val prefSnapshot = firestore!!.collection("users").document(uid).collection("preferences").document("settings").get().await()
            if (prefSnapshot.exists()) {
                handleRemotePreferences(prefSnapshot)
            }

            syncPreferencesSingle(
                currency = cryptoManager.getCurrency(),
                riskFreeRate = cryptoManager.getRiskFreeRate(),
                riskPremium = cryptoManager.getRiskPremium(),
                apiKey = cryptoManager.getApiKey(),
                geminiApiKey = cryptoManager.getGeminiApiKey()
            )

            syncState.value = FirebaseSyncState.Success
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error during cloud synchronization: ${e.message}", e)
            val msg = e.message ?: ""
            val isPermissionDenied = msg.contains("permission-denied", ignoreCase = true) ||
                    msg.contains("permission denied", ignoreCase = true) ||
                    msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
                    msg.contains("insufficient permissions", ignoreCase = true)

            val displayMessage = if (isPermissionDenied) {
                "permission-denied: Cloud Firestore security rules are blocking synchronization. Please configure your Firestore security rules in the Firebase console."
            } else {
                msg.ifEmpty { "Unknown sync error" }
            }
            syncState.value = FirebaseSyncState.Error(displayMessage)
            Result.failure(Exception(displayMessage, e))
        }
    }

    // Instant local changes upload (Write-Through Sync)
    fun syncWatchlistSingle(ticker: WatchlistTickerEntity) {
        val uid = getUserId() ?: return
        if (!isReady()) return
        val data = hashMapOf(
            "symbol" to ticker.symbol,
            "companyName" to ticker.companyName,
            "livePrice" to ticker.livePrice,
            "lastFetched" to ticker.lastFetched,
            "manuallyEnteredCostBasis" to ticker.manuallyEnteredCostBasis,
            "targetPrice" to ticker.targetPrice,
            "logoUrl" to ticker.logoUrl,
            "displayOrder" to ticker.displayOrder,
            "changePercent" to ticker.changePercent
        )
        firestore!!.collection("users").document(uid).collection("watchlist")
            .document(ticker.symbol)
            .set(data, SetOptions.merge())
    }

    fun removeWatchlistSingle(symbol: String) {
        val uid = getUserId() ?: return
        if (!isReady()) return
        firestore!!.collection("users").document(uid).collection("watchlist")
            .document(symbol)
            .delete()
    }

    fun syncTradeSingle(trade: TradeLogEntity) {
        val uid = getUserId() ?: return
        if (!isReady()) return
        val data = hashMapOf(
            "id" to trade.id,
            "ticker" to trade.ticker,
            "tradeType" to trade.tradeType,
            "date" to trade.date,
            "contracts" to trade.contracts,
            "strikePrice" to trade.strikePrice,
            "premiumPerShare" to trade.premiumPerShare,
            "expiryDate" to trade.expiryDate,
            "fees" to trade.fees,
            "netCreditDebit" to trade.netCreditDebit,
            "annualizedReturn" to trade.annualizedReturn,
            "fcfYield" to trade.fcfYield,
            "isClosed" to trade.isClosed,
            "closePremium" to trade.closePremium,
            "closeFees" to trade.closeFees,
            "closeDate" to trade.closeDate,
            "manualOutcome" to trade.manualOutcome
        )
        firestore!!.collection("users").document(uid).collection("trades")
            .document(trade.id.toString())
            .set(data, SetOptions.merge())
    }

    fun removeTradeSingle(id: Int) {
        val uid = getUserId() ?: return
        if (!isReady()) return
        firestore!!.collection("users").document(uid).collection("trades")
            .document(id.toString())
            .delete()
    }

    fun syncSnapshotSingle(snapshot: CalculatorSnapshotEntity) {
        val uid = getUserId() ?: return
        if (!isReady()) return
        val data = hashMapOf(
            "symbol" to snapshot.symbol,
            "currentPrice" to snapshot.currentPrice,
            "fcfPerShare" to snapshot.fcfPerShare,
            "revenuePerShare" to snapshot.revenuePerShare,
            "fcfMarginPercent" to snapshot.fcfMarginPercent,
            "roicPercent" to snapshot.roicPercent,
            "netCashPerShare" to snapshot.netCashPerShare,
            "sharesOutstanding" to snapshot.sharesOutstanding,
            "marketCap" to snapshot.marketCap,
            "fcfGrowthRate" to snapshot.fcfGrowthRate,
            "equityGrowthRate" to snapshot.equityGrowthRate,
            "fundamentalGrowthRate" to snapshot.fundamentalGrowthRate,
            "historicalFcfMargin" to snapshot.historicalFcfMargin,
            "riskFreeRate" to snapshot.riskFreeRate,
            "riskPremium" to snapshot.riskPremium,
            "terminalGrowthRate" to snapshot.terminalGrowthRate,
            "highGrowthYears" to snapshot.highGrowthYears,
            "historicalFcfYield" to snapshot.historicalFcfYield,
            "lastFetched" to snapshot.lastFetched,
            "ttmRevenue" to snapshot.ttmRevenue,
            "ttmFcf" to snapshot.ttmFcf,
            "cashOnHand" to snapshot.cashOnHand,
            "ltDebt" to snapshot.ltDebt,
            "interestCoverage" to snapshot.interestCoverage,
            "ttmNetIncome" to snapshot.ttmNetIncome,
            "checkedQualitativeTitles" to snapshot.checkedQualitativeTitles
        )
        firestore!!.collection("users").document(uid).collection("snapshots")
            .document(snapshot.symbol)
            .set(data, SetOptions.merge())
    }

    fun syncPreferencesSingle(currency: String, riskFreeRate: Float, riskPremium: Float, apiKey: String, geminiApiKey: String) {
        val uid = getUserId() ?: return
        if (!isReady()) return
        val data = hashMapOf(
            "defaultCurrency" to currency,
            "riskFreeRate" to riskFreeRate,
            "riskPremium" to riskPremium,
            "fmpApiKey" to apiKey,
            "geminiApiKey" to geminiApiKey
        )
        firestore!!.collection("users").document(uid).collection("preferences").document("settings")
            .set(data, SetOptions.merge())
    }
}

sealed class FirebaseAuthState {
    object SignedOut : FirebaseAuthState()
    data class SignedIn(val uid: String, val email: String, val displayName: String) : FirebaseAuthState()
}

sealed class FirebaseSyncState {
    object Idle : FirebaseSyncState()
    object Syncing : FirebaseSyncState()
    object Success : FirebaseSyncState()
    data class Error(val message: String) : FirebaseSyncState()
}
