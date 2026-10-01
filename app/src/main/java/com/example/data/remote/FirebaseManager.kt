package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.CryptoManager
import com.example.data.local.AppDatabase
import com.example.data.local.CalculatorSnapshotEntity
import com.example.data.local.TradeLogEntity
import com.example.data.local.WatchlistTickerEntity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
        val cachedUid = cryptoManager.getCachedUserUid()
        if (cachedUid.isNotEmpty()) {
            authState.value = FirebaseAuthState.SignedIn(
                uid = cachedUid,
                email = cryptoManager.getCachedUserEmail(),
                displayName = cryptoManager.getCachedUserDisplayName(),
                photoUrl = cryptoManager.getCachedUserPhotoUrl()
            )
        }
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
                firestore = FirebaseFirestore.getInstance(app, "equityiqmobiledata")
                isInitialized.value = true

                // Setup auth listener
                auth?.addAuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        val photo = extractPhotoUrl(user)
                        val name = extractDisplayName(user)
                        cryptoManager.saveUserProfile(user.uid, user.email ?: "", name, photo)
                        authState.value = FirebaseAuthState.SignedIn(
                            uid = user.uid,
                            email = user.email ?: "",
                            displayName = name,
                            photoUrl = photo
                        )
                        startRealtimeSync(user.uid)
                        if (photo.isNullOrBlank()) {
                            scope.launch {
                                fetchRemoteUserProfile(user.uid)
                            }
                        }
                    } else {
                        val lastGoogle = GoogleSignIn.getLastSignedInAccount(context)
                        if (lastGoogle == null) {
                            cryptoManager.clearCachedUserProfile()
                            authState.value = FirebaseAuthState.SignedOut
                            stopRealtimeSync()
                        }
                    }
                }

                val initialUser = auth?.currentUser
                if (initialUser != null) {
                    val photo = extractPhotoUrl(initialUser)
                    val name = extractDisplayName(initialUser)
                    cryptoManager.saveUserProfile(initialUser.uid, initialUser.email ?: "", name, photo)
                    authState.value = FirebaseAuthState.SignedIn(
                        uid = initialUser.uid,
                        email = initialUser.email ?: "",
                        displayName = name,
                        photoUrl = photo
                    )
                    startRealtimeSync(initialUser.uid)
                    if (photo.isNullOrBlank()) {
                        scope.launch {
                            fetchRemoteUserProfile(initialUser.uid)
                        }
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

    private suspend fun fetchRemoteUserProfile(uid: String) {
        try {
            val userDoc = firestore?.collection("users")?.document(uid)?.get()?.await()
            if (userDoc != null && userDoc.exists()) {
                val remotePhoto = userDoc.getString("photoUrl")
                val remoteName = userDoc.getString("displayName")
                val current = authState.value
                if (!remotePhoto.isNullOrBlank() && current is FirebaseAuthState.SignedIn) {
                    val updatedName = if (current.displayName.isNotBlank()) current.displayName else (remoteName ?: "")
                    cryptoManager.saveUserProfile(current.uid, current.email, updatedName, remotePhoto)
                    authState.value = current.copy(photoUrl = remotePhoto, displayName = updatedName)
                }
            }
        } catch (e: Exception) {
            Log.w("FirebaseManager", "Error fetching remote user profile: ${e.message}")
        }
    }

    private fun extractPhotoUrl(user: FirebaseUser?, photoOverride: String? = null): String? {
        if (!photoOverride.isNullOrBlank()) return photoOverride
        val cached = cryptoManager.getCachedUserPhotoUrl()
        if (!cached.isNullOrBlank()) return cached
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (googleAccount?.photoUrl != null) return googleAccount.photoUrl.toString()
        if (user == null) return null
        val uPhoto = user.photoUrl?.toString()
        if (!uPhoto.isNullOrBlank()) return uPhoto
        val providerPhoto = user.providerData.firstOrNull { it.photoUrl != null }?.photoUrl?.toString()
        if (!providerPhoto.isNullOrBlank()) return providerPhoto
        return null
    }

    private fun extractDisplayName(user: FirebaseUser?, nameOverride: String? = null): String {
        if (!nameOverride.isNullOrBlank()) return nameOverride
        val cached = cryptoManager.getCachedUserDisplayName()
        if (cached.isNotBlank()) return cached
        val fbName = user?.displayName ?: ""
        if (fbName.isNotEmpty()) return fbName
        val providerName = user?.providerData?.firstOrNull { !it.displayName.isNullOrEmpty() }?.displayName ?: ""
        if (providerName.isNotEmpty()) return providerName
        val googleName = GoogleSignIn.getLastSignedInAccount(context)?.displayName ?: ""
        if (googleName.isNotEmpty()) return googleName
        return user?.email ?: ""
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

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        photoUrl: String? = null,
        displayName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isReady()) {
            return@withContext Result.failure(Exception("Firebase not initialized. Please configure settings first."))
        }
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth!!.signInWithCredential(credential).await()
            val user = authResult.user
            if (user != null) {
                val resolvedPhoto = extractPhotoUrl(user, photoUrl)
                val resolvedName = extractDisplayName(user, displayName)
                cryptoManager.saveUserProfile(user.uid, user.email ?: "", resolvedName, resolvedPhoto)
                val userState = FirebaseAuthState.SignedIn(
                    uid = user.uid,
                    email = user.email ?: "",
                    displayName = resolvedName,
                    photoUrl = resolvedPhoto
                )
                authState.value = userState

                // Sync profile metadata to Firestore users collection
                try {
                    val profileData = hashMapOf<String, Any?>(
                        "uid" to user.uid,
                        "email" to (user.email ?: ""),
                        "displayName" to resolvedName,
                        "photoUrl" to resolvedPhoto,
                        "lastSignInTime" to System.currentTimeMillis()
                    )
                    firestore!!.collection("users").document(user.uid)
                        .set(profileData, SetOptions.merge())
                } catch (e: Exception) {
                    Log.w("FirebaseManager", "Could not sync user profile document: ${e.message}")
                }

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
        cryptoManager.clearCachedUserProfile()
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
                        "targetYield" to local.targetYield,
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
            val livePrice = remoteDoc.getDouble("livePrice") ?: (remoteDoc.getLong("livePrice")?.toDouble() ?: 0.0)
            val lastFetched = remoteDoc.getLong("lastFetched") ?: (remoteDoc.getDouble("lastFetched")?.toLong() ?: 0L)
            val manuallyEnteredCostBasis = remoteDoc.getDouble("manuallyEnteredCostBasis") ?: remoteDoc.getLong("manuallyEnteredCostBasis")?.toDouble()
            val targetPrice = remoteDoc.getDouble("targetPrice") ?: remoteDoc.getLong("targetPrice")?.toDouble()
            val targetYield = remoteDoc.getDouble("targetYield") ?: remoteDoc.getLong("targetYield")?.toDouble()
            val logoUrl = remoteDoc.getString("logoUrl")
            val displayOrder = remoteDoc.getLong("displayOrder")?.toInt() ?: (remoteDoc.getDouble("displayOrder")?.toInt() ?: 0)
            val changePercent = remoteDoc.getDouble("changePercent") ?: remoteDoc.getLong("changePercent")?.toDouble()

            if (local == null) {
                val newEntity = WatchlistTickerEntity(
                    symbol = symbol,
                    companyName = companyName,
                    livePrice = livePrice,
                    lastFetched = lastFetched,
                    manuallyEnteredCostBasis = manuallyEnteredCostBasis,
                    targetPrice = targetPrice,
                    targetYield = targetYield,
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
                if (local.targetPrice != targetPrice || local.targetYield != targetYield) {
                    tickerDao.updateTargetPriceAndYield(symbol, targetPrice, targetYield)
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
                    if (local.id <= 0) continue
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

        val remoteIds = snapshot.documents.mapNotNull { it.id.toIntOrNull() }.filter { it > 0 }.toSet()

        for (remoteDoc in snapshot.documents) {
            val remoteId = remoteDoc.id.toIntOrNull() ?: continue
            if (remoteId <= 0) {
                // Delete legacy/corrupt "0" doc from Firestore
                firestore?.collection("users")?.document(uid)?.collection("trades")?.document(remoteDoc.id)?.delete()
                continue
            }
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
                currentPrice = remoteDoc.getDouble("currentPrice") ?: (remoteDoc.getLong("currentPrice")?.toDouble() ?: 0.0),
                fcfPerShare = remoteDoc.getDouble("fcfPerShare") ?: (remoteDoc.getLong("fcfPerShare")?.toDouble() ?: 0.0),
                revenuePerShare = remoteDoc.getDouble("revenuePerShare") ?: (remoteDoc.getLong("revenuePerShare")?.toDouble() ?: 0.0),
                fcfMarginPercent = remoteDoc.getDouble("fcfMarginPercent") ?: (remoteDoc.getLong("fcfMarginPercent")?.toDouble() ?: 0.0),
                roicPercent = remoteDoc.getDouble("roicPercent") ?: (remoteDoc.getLong("roicPercent")?.toDouble() ?: 0.0),
                netCashPerShare = remoteDoc.getDouble("netCashPerShare") ?: (remoteDoc.getLong("netCashPerShare")?.toDouble() ?: 0.0),
                sharesOutstanding = remoteDoc.getDouble("sharesOutstanding") ?: (remoteDoc.getLong("sharesOutstanding")?.toDouble() ?: 0.0),
                marketCap = remoteDoc.getDouble("marketCap") ?: (remoteDoc.getLong("marketCap")?.toDouble() ?: 0.0),
                fcfGrowthRate = remoteDoc.getDouble("fcfGrowthRate") ?: (remoteDoc.getLong("fcfGrowthRate")?.toDouble() ?: 0.0),
                equityGrowthRate = remoteDoc.getDouble("equityGrowthRate") ?: (remoteDoc.getLong("equityGrowthRate")?.toDouble() ?: 0.0),
                fundamentalGrowthRate = remoteDoc.getDouble("fundamentalGrowthRate") ?: (remoteDoc.getLong("fundamentalGrowthRate")?.toDouble() ?: 0.0),
                historicalFcfMargin = remoteDoc.getDouble("historicalFcfMargin") ?: (remoteDoc.getLong("historicalFcfMargin")?.toDouble() ?: 0.0),
                riskFreeRate = remoteDoc.getDouble("riskFreeRate") ?: (remoteDoc.getLong("riskFreeRate")?.toDouble() ?: 0.0),
                riskPremium = remoteDoc.getDouble("riskPremium") ?: (remoteDoc.getLong("riskPremium")?.toDouble() ?: 0.0),
                terminalGrowthRate = remoteDoc.getDouble("terminalGrowthRate") ?: (remoteDoc.getLong("terminalGrowthRate")?.toDouble() ?: 0.0),
                highGrowthYears = remoteDoc.getLong("highGrowthYears")?.toInt() ?: (remoteDoc.getDouble("highGrowthYears")?.toInt() ?: 10),
                historicalFcfYield = remoteDoc.getDouble("historicalFcfYield") ?: (remoteDoc.getLong("historicalFcfYield")?.toDouble() ?: 0.0),
                lastFetched = remoteDoc.getLong("lastFetched") ?: (remoteDoc.getDouble("lastFetched")?.toLong() ?: 0L),
                ttmRevenue = remoteDoc.getDouble("ttmRevenue") ?: (remoteDoc.getLong("ttmRevenue")?.toDouble() ?: 0.0),
                ttmFcf = remoteDoc.getDouble("ttmFcf") ?: (remoteDoc.getLong("ttmFcf")?.toDouble() ?: 0.0),
                cashOnHand = remoteDoc.getDouble("cashOnHand") ?: (remoteDoc.getLong("cashOnHand")?.toDouble() ?: 0.0),
                ltDebt = remoteDoc.getDouble("ltDebt") ?: (remoteDoc.getLong("ltDebt")?.toDouble() ?: 0.0),
                interestCoverage = remoteDoc.getDouble("interestCoverage") ?: (remoteDoc.getLong("interestCoverage")?.toDouble() ?: 0.0),
                ttmNetIncome = remoteDoc.getDouble("ttmNetIncome") ?: (remoteDoc.getLong("ttmNetIncome")?.toDouble() ?: 0.0),
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
            "targetYield" to ticker.targetYield,
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
        if (!isReady() || trade.id <= 0) return
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
        if (!isReady() || id <= 0) return
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
    data class SignedIn(
        val uid: String,
        val email: String,
        val displayName: String,
        val photoUrl: String? = null
    ) : FirebaseAuthState()
}

sealed class FirebaseSyncState {
    object Idle : FirebaseSyncState()
    object Syncing : FirebaseSyncState()
    object Success : FirebaseSyncState()
    data class Error(val message: String) : FirebaseSyncState()
}
