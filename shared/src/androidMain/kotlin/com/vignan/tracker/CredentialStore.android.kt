package com.vignan.tracker

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object AppContextProvider {
    var context: Context? = null
}

class AndroidCredentialStore(private val contextProvider: () -> Context?) : CredentialStore {
    private var inMemoryCreds: StoredCredentials? = null
    private var inMemoryCache: String? = null

    private fun getPrefs(): SharedPreferences? {
        val ctx = contextProvider() ?: return null
        return try {
            EncryptedSharedPreferences.create(
                ctx,
                "viit_secure_prefs",
                MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            ctx.getSharedPreferences("viit_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    override suspend fun load(): StoredCredentials? {
        val prefs = getPrefs() ?: return inMemoryCreds
        val roll = prefs.getString("roll", null) ?: return inMemoryCreds
        val pass = prefs.getString("pwd", null) ?: return inMemoryCreds
        val rememberMe = prefs.getBoolean("remember_me", true)
        val timestamp = prefs.getLong("last_login_ts", System.currentTimeMillis())
        return StoredCredentials(roll, pass, rememberMe, timestamp)
    }

    override suspend fun save(creds: StoredCredentials) {
        inMemoryCreds = creds
        val prefs = getPrefs() ?: return
        prefs.edit()
            .putString("roll", creds.rollNo)
            .putString("pwd", creds.password)
            .putBoolean("remember_me", creds.rememberMe)
            .putLong("last_login_ts", creds.lastLoginTimestamp)
            .apply()
    }

    override suspend fun clear() {
        inMemoryCreds = null
        inMemoryCache = null
        val prefs = getPrefs() ?: return
        prefs.edit().clear().apply()
    }

    override suspend fun getCachedAttendance(): String? {
        val prefs = getPrefs() ?: return inMemoryCache
        return prefs.getString("cached_attendance_json", null) ?: inMemoryCache
    }

    override suspend fun saveCachedAttendance(json: String) {
        inMemoryCache = json
        val prefs = getPrefs() ?: return
        prefs.edit().putString("cached_attendance_json", json).apply()
    }
}

private val androidStore = AndroidCredentialStore { AppContextProvider.context }

actual fun getCredentialStore(): CredentialStore = androidStore
