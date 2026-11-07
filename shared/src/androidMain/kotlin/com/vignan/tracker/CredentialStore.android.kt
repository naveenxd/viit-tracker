package com.vignan.tracker

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppContextProvider {
    var context: Context? = null
}

class AndroidCredentialStore(private val contextProvider: () -> Context?) : CredentialStore {
    private var inMemoryCreds: StoredCredentials? = null
    private var inMemoryCache: String? = null
    private var inMemoryTargetPct: Double? = null
    private val inMemoryResponses = mutableMapOf<String, String>()

    // EncryptedSharedPreferences creation runs master-key crypto — expensive.
    // Build once and reuse; rebuilding per call was a measurable cold-start cost.
    private var cachedPrefs: SharedPreferences? = null

    // If the keystore/encrypted store is unusable, stay memory-only. Never
    // fall back to plaintext SharedPreferences: that silently put the portal
    // password on disk unencrypted when creation failed once.
    private var prefsUnavailable = false
    private var legacyFallbackCleaned = false

    @Synchronized
    private fun getPrefs(): SharedPreferences? {
        cachedPrefs?.let { return it }
        if (prefsUnavailable) return null
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
            ).also {
                cachedPrefs = it
                trimReportCache(it)
                deleteLegacyPlaintextFallback(ctx)
            }
        } catch (e: Exception) {
            // Credentials stay in memory for this process; a restart asks for
            // the password again rather than persisting it in the clear.
            prefsUnavailable = true
            deleteLegacyPlaintextFallback(ctx)
            null
        }
    }

    private fun trimReportCache(prefs: SharedPreferences, incoming: String? = null) {
        val stored = prefs.all.keys.filter { it.startsWith("cached_response_report_v1_") }
            .map { it.removePrefix("cached_response_") }
        val order = prefs.getString("report_cache_order", null)?.split('|').orEmpty()
        val keep = recentReportKeys(order.filter { it in stored } + stored.filterNot { it in order }, incoming)
        prefs.edit().apply {
            stored.filterNot { it in keep }.forEach { remove("cached_response_$it") }
            putString("report_cache_order", keep.joinToString("|"))
        }.apply()
    }

    /** Remove the plaintext file written by builds before the fallback was removed. */
    private fun deleteLegacyPlaintextFallback(ctx: Context) {
        if (legacyFallbackCleaned) return
        legacyFallbackCleaned = true
        runCatching { ctx.deleteSharedPreferences(LEGACY_PLAINTEXT_PREFS) }
    }

    override suspend fun load(): StoredCredentials? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized inMemoryCreds
            val roll = prefs.getString("roll", null) ?: return@synchronized inMemoryCreds
            val pass = prefs.getString("pwd", null) ?: return@synchronized inMemoryCreds
            val rememberMe = prefs.getBoolean("remember_me", true)
            val timestamp = prefs.getLong("last_login_ts", System.currentTimeMillis())
            return@synchronized StoredCredentials(roll, pass, rememberMe, timestamp)
        }
    }

    override suspend fun save(creds: StoredCredentials): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            inMemoryCreds = creds
            val prefs = getPrefs() ?: return@synchronized
            prefs.edit()
                .putString("roll", creds.rollNo)
                .putString("pwd", creds.password)
                .putBoolean("remember_me", creds.rememberMe)
                .putLong("last_login_ts", creds.lastLoginTimestamp)
                .apply()
        }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            inMemoryCreds = null
            inMemoryCache = null
            inMemoryTargetPct = null
            inMemoryResponses.clear()
            contextProvider()?.let { deleteLegacyPlaintextFallback(it) }
            val prefs = getPrefs() ?: return@synchronized
            prefs.edit().clear().apply()
        }
    }

    override suspend fun getCachedAttendance(): String? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized inMemoryCache
            return@synchronized prefs.getString("cached_attendance_json", null) ?: inMemoryCache
        }
    }

    override suspend fun saveCachedAttendance(json: String): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            inMemoryCache = json
            val prefs = getPrefs() ?: return@synchronized
            prefs.edit().putString("cached_attendance_json", json).apply()
        }
    }

    override suspend fun getCachedResponse(key: String): String? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized inMemoryResponses[key]
            return@synchronized prefs.getString("cached_response_$key", null) ?: inMemoryResponses[key]
        }
    }

    override suspend fun saveCachedResponse(key: String, json: String): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            inMemoryResponses.remove(key)
            inMemoryResponses[key] = json
            val prefs = getPrefs()
            if (key.startsWith("report_v1_")) {
                val keep = recentReportKeys(inMemoryResponses.keys.filter { it.startsWith("report_v1_") }, key)
                inMemoryResponses.keys.removeAll { it.startsWith("report_v1_") && it !in keep }
                if (prefs != null) trimReportCache(prefs, key)
            }
            prefs?.edit()?.putString("cached_response_$key", json)?.apply()
        }
    }

    override suspend fun getTargetPct(): Double? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            // A display preference, not a credential: keep it in UI prefs so
            // logging out does not reset it (clear() only wipes the secure file).
            val prefs = contextProvider()
                ?.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                ?: return@synchronized inMemoryTargetPct
            return@synchronized prefs.getString("target_pct", null)?.toDoubleOrNull() ?: inMemoryTargetPct
        }
    }

    override suspend fun saveTargetPct(percentage: Double): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            inMemoryTargetPct = percentage
            contextProvider()
                ?.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putString("target_pct", percentage.toString())?.apply()
        }
    }

    override suspend fun getApiBaseUrl(): String? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized null
            return@synchronized prefs.getString("api_base_url", null)
        }
    }

    override suspend fun saveApiBaseUrl(url: String): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized
            prefs.edit().putString("api_base_url", url).apply()
        }
    }

    override suspend fun getUse24HourClock(): Boolean? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized null
            return@synchronized if (prefs.contains("use_24h_clock")) prefs.getBoolean("use_24h_clock", false) else null
        }
    }

    override suspend fun saveUse24HourClock(enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized
            prefs.edit().putBoolean("use_24h_clock", enabled).apply()
        }
    }

    override suspend fun getThemeMode(): String? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            // The unencrypted mirror is authoritative: saveThemeMode writes it
            // first, so it can never lag the encrypted copy (a kill between the
            // two queued writes flushes the mirror ahead of it). Reading the same
            // value as primeThemeMode keeps the field and the palette from ever
            // disagreeing about the active mode.
            peekThemeMode()?.let { return@synchronized it }
            val prefs = getPrefs() ?: return@synchronized null
            return@synchronized prefs.getString("theme_mode", null)
        }
    }

    override suspend fun saveThemeMode(id: String): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            // Mirror first: this is the copy the next cold start reads synchronously,
            // so it must be on disk even if the encrypted write below never lands.
            contextProvider()?.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putString("theme_mode", id)?.apply()

            val prefs = getPrefs() ?: return@synchronized
            prefs.edit().putString("theme_mode", id).apply()
        }
    }

    override fun peekThemeMode(): String? =
        contextProvider()
            ?.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            ?.getString("theme_mode", null)

    override suspend fun getAmoledDark(): Boolean? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            // Mirror-authoritative, for the same reason as [getThemeMode].
            peekAmoledDark()?.let { return@synchronized it }
            val prefs = getPrefs() ?: return@synchronized null
            return@synchronized if (prefs.contains("amoled_dark")) prefs.getBoolean("amoled_dark", false) else null
        }
    }

    override suspend fun saveAmoledDark(enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            contextProvider()?.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean("amoled_dark", enabled)?.apply()

            val prefs = getPrefs() ?: return@synchronized
            prefs.edit().putBoolean("amoled_dark", enabled).apply()
        }
    }

    override fun peekAmoledDark(): Boolean? {
        val prefs = contextProvider()
            ?.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            ?: return null
        return if (prefs.contains("amoled_dark")) prefs.getBoolean("amoled_dark", false) else null
    }

    override suspend fun getRegisterTotalsPinned(): Boolean? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized null
            return@synchronized if (prefs.contains("register_totals_pinned")) {
                prefs.getBoolean("register_totals_pinned", false)
            } else {
                null
            }
        }
    }

    override suspend fun saveRegisterTotalsPinned(enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized
            prefs.edit().putBoolean("register_totals_pinned", enabled).apply()
        }
    }

    override suspend fun getMotionEnabled(): Boolean? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            // Mirror-first like the theme: the very first frame decides whether the
            // app's entrance animations run, so this must be readable pre-composition.
            peekMotionEnabled()?.let { return@synchronized it }
            val prefs = getPrefs() ?: return@synchronized null
            return@synchronized if (prefs.contains("motion_enabled")) {
                prefs.getBoolean("motion_enabled", true)
            } else {
                null
            }
        }
    }

    override suspend fun saveMotionEnabled(enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            contextProvider()?.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean("motion_enabled", enabled)?.apply()

            val prefs = getPrefs() ?: return@synchronized
            prefs.edit().putBoolean("motion_enabled", enabled).apply()
        }
    }

    override fun peekMotionEnabled(): Boolean? {
        val prefs = contextProvider()
            ?.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            ?: return null
        return if (prefs.contains("motion_enabled")) prefs.getBoolean("motion_enabled", true) else null
    }

    override suspend fun getSundayClassesEnabled(): Boolean? = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized null
            return@synchronized if (prefs.contains("sunday_classes_enabled")) {
                prefs.getBoolean("sunday_classes_enabled", false)
            } else {
                null
            }
        }
    }

    override suspend fun saveSundayClassesEnabled(enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        synchronized(this@AndroidCredentialStore) {
            val prefs = getPrefs() ?: return@synchronized
            prefs.edit().putBoolean("sunday_classes_enabled", enabled).apply()
        }
    }

    private companion object {
        /** Unencrypted prefs holding only non-secret UI state, readable pre-composition. */
        const val UI_PREFS = "viit_ui_prefs"

        /** Old insecure fallback file; deleted on first successful (or failed) open. */
        const val LEGACY_PLAINTEXT_PREFS = "viit_secure_prefs_fallback"
    }
}

private val androidStore = AndroidCredentialStore { AppContextProvider.context }

actual fun getCredentialStore(): CredentialStore = androidStore
