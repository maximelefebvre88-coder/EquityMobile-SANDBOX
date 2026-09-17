package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.CryptoManager
import com.example.data.local.AppDatabase
import com.example.data.local.CalculatorSnapshotEntity
import com.example.data.local.TradeLogEntity
import com.example.data.local.WatchlistTickerEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseManager(private val context: Context) {

    private val cryptoManager = CryptoManager(context)
    private val db = AppDatabase.getDatabase(context)

    val isInitialized = MutableStateFlow(false)
    val authState = MutableStateFlow<FirebaseAuthState>(FirebaseAuthState.SignedOut)
    val syncState = MutableStateFlow<FirebaseSyncState>(FirebaseSyncState.Idle)

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null

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
                    } else {
                        authState.value = FirebaseAuthState.SignedOut
                    }
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
        val key = com.example.BuildConfig.FIREBASE_API_KEY
        if (key.isNotEmpty() && key != "YOUR_FIREBASE_API_KEY_HERE") return key
        return cryptoManager.getFirebaseApiKey()
    }

    fun getFirebaseProjectId(): String {
        val resVal = getStringResourceByName("project_id")
        if (resVal.isNotEmpty()) return resVal
        val projId = com.example.BuildConfig.FIREBASE_PROJECT_ID
        if (projId.isNotEmpty() && projId != "YOUR_FIREBASE_PROJECT_ID_HERE") return projId
        return cryptoManager.getFirebaseProjectId()
    }

    fun getFirebaseAppId(): String {
        val resVal = getStringResourceByName("google_app_id")
        if (resVal.isNotEmpty()) return resVal
        val appId = com.example.BuildConfig.FIREBASE_APP_ID
        if (appId.isNotEmpty() && appId != "YOUR_FIREBASE_APP_ID_HERE") return appId
        return cryptoManager.getFirebaseAppId()
    }

    fun getFirebaseAuthClientId(): String {
        val resVal = getStringResourceByName("default_web_client_id")
        if (resVal.isNotEmpty()) return resVal
        val clientId = com.example.BuildConfig.FIREBASE_CLIENT_ID
        if (clientId.isNotEmpty() && clientId != "YOUR_FIREBASE_CLIENT_ID_HERE") return clientId
        return cryptoManager.getFirebaseAuthClientId()
    }

    fun saveFirebaseConfig(apiKey: String, projectId: String, appId: String, clientId: String) {
        cryptoManager.saveFirebaseConfig(apiKey, projectId, appId, clientId)
        
        // Re-initialize Firebase with new settings
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
                // Auto-sync after signing in
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
        auth?.signOut()
        authState.value = FirebaseAuthState.SignedOut
    }

    // Bidirectional Cloud Sync
    suspend fun syncDataAcrossDevices(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isReady() || auth?.currentUser == null) {
            return@withContext Result.failure(Exception("User is not signed in or Firebase is not ready."))
        }

        val uid = auth!!.currentUser!!.uid
        syncState.value = FirebaseSyncState.Syncing

        try {
            // 1. Sync Watchlist
            syncWatchlist(uid)

            // 2. Sync Trade Logs
            syncTrades(uid)

            // 3. Sync Calculator Snapshots
            syncSnapshots(uid)

            // 4. Sync Preferences/Settings
            syncPreferences(uid)

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

    private suspend fun syncWatchlist(uid: String) {
        val tickerDao = db.tickerDao()
        val localTickers = tickerDao.getAllTickersNonFlow()
        val watchlistRef = firestore!!.collection("users").document(uid).collection("watchlist")

        // Retrieve remote watchlist
        val remoteSnapshot = watchlistRef.get().await()
        val remoteMap = remoteSnapshot.documents.associateBy { it.id }

        // Send local to cloud
        for (local in localTickers) {
            val data = hashMapOf(
                "symbol" to local.symbol,
                "companyName" to local.companyName,
                "livePrice" to local.livePrice,
                "lastFetched" to local.lastFetched,
                "manuallyEnteredCostBasis" to local.manuallyEnteredCostBasis,
                "targetPrice" to local.targetPrice,
                "logoUrl" to local.logoUrl,
                "displayOrder" to local.displayOrder
            )
            watchlistRef.document(local.symbol).set(data, SetOptions.merge()).await()
        }

        // Pull new remote items to local
        for (remoteDoc in remoteSnapshot.documents) {
            val symbol = remoteDoc.id
            val existsLocally = localTickers.any { it.symbol == symbol }
            if (!existsLocally) {
                val ticker = WatchlistTickerEntity(
                    symbol = symbol,
                    companyName = remoteDoc.getString("companyName") ?: symbol,
                    livePrice = remoteDoc.getDouble("livePrice") ?: 0.0,
                    lastFetched = remoteDoc.getLong("lastFetched") ?: 0L,
                    manuallyEnteredCostBasis = remoteDoc.getDouble("manuallyEnteredCostBasis"),
                    targetPrice = remoteDoc.getDouble("targetPrice"),
                    logoUrl = remoteDoc.getString("logoUrl"),
                    displayOrder = remoteDoc.getLong("displayOrder")?.toInt() ?: 0,
                    changePercent = remoteDoc.getDouble("changePercent")
                )
                tickerDao.insert(ticker)
            } else {
                // Merge manuallyEnteredCostBasis, targetPrice, and displayOrder if remote is more recent or filled
                val local = localTickers.find { it.symbol == symbol }
                if (local != null) {
                    val remoteCostBasis = remoteDoc.getDouble("manuallyEnteredCostBasis")
                    if (remoteCostBasis != null && remoteCostBasis != local.manuallyEnteredCostBasis) {
                        tickerDao.updateCostBasis(symbol, remoteCostBasis)
                    }
                    val remoteTargetPrice = remoteDoc.getDouble("targetPrice")
                    if (remoteTargetPrice != null && remoteTargetPrice != local.targetPrice) {
                        tickerDao.updateTargetPrice(symbol, remoteTargetPrice)
                    }
                }
            }
        }
    }

    private suspend fun syncTrades(uid: String) {
        val tradeDao = db.tradeDao()
        val trades = tradeDao.getAllTrades().first()
        val tradesRef = firestore!!.collection("users").document(uid).collection("trades")

        // Upload local trades to cloud
        for (local in trades) {
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
            // Use local.id as document name so they sync properly
            tradesRef.document(local.id.toString()).set(data, SetOptions.merge()).await()
        }

        // Pull remote trades to local
        val remoteSnapshot = tradesRef.get().await()
        for (remoteDoc in remoteSnapshot.documents) {
            val remoteIdStr = remoteDoc.id
            val remoteId = remoteIdStr.toIntOrNull() ?: continue
            val existsLocally = trades.any { it.id == remoteId }
            if (!existsLocally) {
                val trade = TradeLogEntity(
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
                tradeDao.insertTrade(trade)
            }
        }
    }

    private suspend fun syncSnapshots(uid: String) {
        val snapshotDao = db.calculatorSnapshotDao()
        val localSnapshots = snapshotDao.getAllSnapshotsFlow().first()
        val snapshotsRef = firestore!!.collection("users").document(uid).collection("snapshots")

        // Upload local to cloud
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
            snapshotsRef.document(local.symbol).set(data, SetOptions.merge()).await()
        }

        // Pull remote to local
        val remoteSnapshot = snapshotsRef.get().await()
        for (remoteDoc in remoteSnapshot.documents) {
            val symbol = remoteDoc.id
            val existsLocally = localSnapshots.any { it.symbol == symbol }
            if (!existsLocally) {
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
        }
    }

    private suspend fun syncPreferences(uid: String) {
        val prefRef = firestore!!.collection("users").document(uid).collection("preferences").document("settings")

        // Retrieve remote preferences
        val remoteSnapshot = prefRef.get().await()
        if (remoteSnapshot.exists()) {
            val remoteCurrency = remoteSnapshot.getString("defaultCurrency")
            val remoteRiskFree = remoteSnapshot.getDouble("riskFreeRate")?.toFloat()
            val remoteRiskPremium = remoteSnapshot.getDouble("riskPremium")?.toFloat()
            val remoteApiKey = remoteSnapshot.getString("fmpApiKey")
            val remoteGeminiApiKey = remoteSnapshot.getString("geminiApiKey")

            if (remoteCurrency != null) cryptoManager.saveCurrency(remoteCurrency)
            if (remoteRiskFree != null) cryptoManager.saveRiskFreeRate(remoteRiskFree)
            if (remoteRiskPremium != null) cryptoManager.saveRiskPremium(remoteRiskPremium)
            if (remoteApiKey != null && remoteApiKey.isNotEmpty() && cryptoManager.getApiKey().isEmpty()) {
                cryptoManager.saveApiKey(remoteApiKey)
            }
            if (remoteGeminiApiKey != null && remoteGeminiApiKey.isNotEmpty() && cryptoManager.getGeminiApiKey().isEmpty()) {
                cryptoManager.saveGeminiApiKey(remoteGeminiApiKey)
            }
        }

        // Upload local preferences to cloud
        val localData = hashMapOf(
            "defaultCurrency" to cryptoManager.getCurrency(),
            "riskFreeRate" to cryptoManager.getRiskFreeRate(),
            "riskPremium" to cryptoManager.getRiskPremium(),
            "fmpApiKey" to cryptoManager.getApiKey(),
            "geminiApiKey" to cryptoManager.getGeminiApiKey()
        )
        prefRef.set(localData, SetOptions.merge()).await()
    }

    // Instant local changes upload (Write-Through Sync)
    fun syncWatchlistSingle(ticker: WatchlistTickerEntity) {
        val uid = getUserId() ?: return
        if (!isReady()) return
        firestore!!.collection("users").document(uid).collection("watchlist")
            .document(ticker.symbol)
            .set(ticker, SetOptions.merge())
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
        firestore!!.collection("users").document(uid).collection("trades")
            .document(trade.id.toString())
            .set(trade, SetOptions.merge())
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
        firestore!!.collection("users").document(uid).collection("snapshots")
            .document(snapshot.symbol)
            .set(snapshot, SetOptions.merge())
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
