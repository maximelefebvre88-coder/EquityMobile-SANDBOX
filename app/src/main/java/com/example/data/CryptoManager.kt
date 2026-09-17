package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class CryptoManager(private val context: Context) {
    
    private val securePrefs: SharedPreferences? = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "wheel_iq_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }

    private val regularPrefs: SharedPreferences = context.getSharedPreferences("wheel_iq_regular_prefs", Context.MODE_PRIVATE)

    private fun cleanKey(key: String): String {
        return key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    }

    fun saveApiKey(apiKey: String) {
        val sanitized = cleanKey(apiKey)
        securePrefs?.edit()
            ?.putString("FINNHUB_API_KEY", sanitized)
            ?.putString("FMP_API_KEY", sanitized)
            ?.putString("API_KEY", sanitized)
            ?.apply()
        regularPrefs.edit()
            .putString("FINNHUB_API_KEY", sanitized)
            .putString("FMP_API_KEY", sanitized)
            .putString("API_KEY", sanitized)
            .apply()
    }

    fun getApiKey(): String {
        val keys = listOf("FINNHUB_API_KEY", "FMP_API_KEY", "API_KEY")
        for (k in keys) {
            val v = securePrefs?.getString(k, "") ?: ""
            if (v.isNotEmpty()) return cleanKey(v)
        }
        for (k in keys) {
            val v = regularPrefs.getString(k, "") ?: ""
            if (v.isNotEmpty()) return cleanKey(v)
        }
        return ""
    }

    fun saveGeminiApiKey(apiKey: String) {
        val sanitized = cleanKey(apiKey)
        securePrefs?.edit()
            ?.putString("GEMINI_API_KEY", sanitized)
            ?.putString("AI_API_KEY", sanitized)
            ?.apply()
        regularPrefs.edit()
            .putString("GEMINI_API_KEY", sanitized)
            .putString("AI_API_KEY", sanitized)
            .apply()
    }

    fun getGeminiApiKey(): String {
        val keys = listOf("GEMINI_API_KEY", "AI_API_KEY")
        for (k in keys) {
            val v = securePrefs?.getString(k, "") ?: ""
            if (v.isNotEmpty()) return cleanKey(v)
        }
        for (k in keys) {
            val v = regularPrefs.getString(k, "") ?: ""
            if (v.isNotEmpty()) return cleanKey(v)
        }
        return ""
    }

    fun saveCurrency(currency: String) {
        val c = cleanKey(currency).ifEmpty { "CAD" }
        securePrefs?.edit()?.putString("DEFAULT_CURRENCY", c)?.apply()
        regularPrefs.edit().putString("DEFAULT_CURRENCY", c).apply()
    }

    fun getCurrency(): String {
        val v = securePrefs?.getString("DEFAULT_CURRENCY", "") ?: ""
        if (v.isNotEmpty()) return v
        return regularPrefs.getString("DEFAULT_CURRENCY", "CAD") ?: "CAD"
    }

    fun saveRiskFreeRate(rate: Float) {
        securePrefs?.edit()?.putFloat("RISK_FREE_RATE", rate)?.apply()
        regularPrefs.edit().putFloat("RISK_FREE_RATE", rate).apply()
    }

    fun getRiskFreeRate(): Float {
        if (securePrefs != null && securePrefs.contains("RISK_FREE_RATE")) {
            return securePrefs.getFloat("RISK_FREE_RATE", 4.05f)
        }
        return regularPrefs.getFloat("RISK_FREE_RATE", 4.05f)
    }

    fun saveRiskPremium(premium: Float) {
        securePrefs?.edit()?.putFloat("RISK_PREMIUM", premium)?.apply()
        regularPrefs.edit().putFloat("RISK_PREMIUM", premium).apply()
    }

    fun getRiskPremium(): Float {
        if (securePrefs != null && securePrefs.contains("RISK_PREMIUM")) {
            return securePrefs.getFloat("RISK_PREMIUM", 3.75f)
        }
        return regularPrefs.getFloat("RISK_PREMIUM", 3.75f)
    }

    // Firebase Credentials Settings
    fun getFirebaseApiKey(): String {
        val v = securePrefs?.getString("FIREBASE_API_KEY", "") ?: ""
        if (v.isNotEmpty()) return cleanKey(v)
        return cleanKey(regularPrefs.getString("FIREBASE_API_KEY", "") ?: "")
    }

    fun getFirebaseProjectId(): String {
        val v = securePrefs?.getString("FIREBASE_PROJECT_ID", "") ?: ""
        if (v.isNotEmpty()) return cleanKey(v)
        return cleanKey(regularPrefs.getString("FIREBASE_PROJECT_ID", "") ?: "")
    }

    fun getFirebaseAppId(): String {
        val v = securePrefs?.getString("FIREBASE_APP_ID", "") ?: ""
        if (v.isNotEmpty()) return cleanKey(v)
        return cleanKey(regularPrefs.getString("FIREBASE_APP_ID", "") ?: "")
    }

    fun getFirebaseAuthClientId(): String {
        val v = securePrefs?.getString("FIREBASE_CLIENT_ID", "") ?: ""
        if (v.isNotEmpty()) return cleanKey(v)
        return cleanKey(regularPrefs.getString("FIREBASE_CLIENT_ID", "") ?: "")
    }

    fun saveFirebaseConfig(apiKey: String, projectId: String, appId: String, clientId: String) {
        val cleanApiKey = cleanKey(apiKey)
        val cleanProjId = cleanKey(projectId)
        val cleanAppId = cleanKey(appId)
        val cleanCliId = cleanKey(clientId)
        securePrefs?.edit()
            ?.putString("FIREBASE_API_KEY", cleanApiKey)
            ?.putString("FIREBASE_PROJECT_ID", cleanProjId)
            ?.putString("FIREBASE_APP_ID", cleanAppId)
            ?.putString("FIREBASE_CLIENT_ID", cleanCliId)
            ?.apply()
        regularPrefs.edit()
            .putString("FIREBASE_API_KEY", cleanApiKey)
            .putString("FIREBASE_PROJECT_ID", cleanProjId)
            .putString("FIREBASE_APP_ID", cleanAppId)
            .putString("FIREBASE_CLIENT_ID", cleanCliId)
            .apply()
    }

    // User Profile Cache Persistence
    fun saveUserProfile(uid: String, email: String, displayName: String, photoUrl: String?) {
        val cleanUid = cleanKey(uid)
        val cleanEmail = cleanKey(email)
        val cleanName = cleanKey(displayName)
        val cleanPhoto = photoUrl?.let { cleanKey(it) } ?: ""
        securePrefs?.edit()
            ?.putString("CACHED_USER_UID", cleanUid)
            ?.putString("CACHED_USER_EMAIL", cleanEmail)
            ?.putString("CACHED_USER_NAME", cleanName)
            ?.putString("CACHED_USER_PHOTO", cleanPhoto)
            ?.apply()
        regularPrefs.edit()
            .putString("CACHED_USER_UID", cleanUid)
            .putString("CACHED_USER_EMAIL", cleanEmail)
            .putString("CACHED_USER_NAME", cleanName)
            .putString("CACHED_USER_PHOTO", cleanPhoto)
            .apply()
    }

    fun getCachedUserUid(): String {
        val v = securePrefs?.getString("CACHED_USER_UID", "") ?: ""
        if (v.isNotEmpty()) return cleanKey(v)
        return cleanKey(regularPrefs.getString("CACHED_USER_UID", "") ?: "")
    }

    fun getCachedUserEmail(): String {
        val v = securePrefs?.getString("CACHED_USER_EMAIL", "") ?: ""
        if (v.isNotEmpty()) return cleanKey(v)
        return cleanKey(regularPrefs.getString("CACHED_USER_EMAIL", "") ?: "")
    }

    fun getCachedUserDisplayName(): String {
        val v = securePrefs?.getString("CACHED_USER_NAME", "") ?: ""
        if (v.isNotEmpty()) return cleanKey(v)
        return cleanKey(regularPrefs.getString("CACHED_USER_NAME", "") ?: "")
    }

    fun getCachedUserPhotoUrl(): String? {
        val v = securePrefs?.getString("CACHED_USER_PHOTO", "") ?: ""
        if (v.isNotEmpty()) return cleanKey(v).ifEmpty { null }
        val reg = regularPrefs.getString("CACHED_USER_PHOTO", "") ?: ""
        return cleanKey(reg).ifEmpty { null }
    }

    fun clearCachedUserProfile() {
        securePrefs?.edit()
            ?.remove("CACHED_USER_UID")
            ?.remove("CACHED_USER_EMAIL")
            ?.remove("CACHED_USER_NAME")
            ?.remove("CACHED_USER_PHOTO")
            ?.apply()
        regularPrefs.edit()
            .remove("CACHED_USER_UID")
            .remove("CACHED_USER_EMAIL")
            .remove("CACHED_USER_NAME")
            .remove("CACHED_USER_PHOTO")
            .apply()
    }
}

