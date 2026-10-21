package com.vignan.tracker.analytics

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.vignan.tracker.AppVersion
import com.vignan.tracker.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal, privacy-first install/activity pinger.
 *
 * - One successful check-in per IST calendar day, plus one immediately after a
 *   fresh install or app update.
 * - Sends only a client-side SHA-256 device hash. No IP, location, locale, or
 *   account data is collected by the app.
 * - Debug builds never ping. Builds without an analytics key skip silently.
 * - Failures are retried later, never queued or persisted as an outbox.
 */
object AnalyticsPinger {

    private const val PREFS = "viit_analytics"
    private const val KEY_LAST_DAY = "last_ping_day"
    private const val KEY_LAST_VERSION = "last_version_code"
    private const val KEY_LAST_ATTEMPT = "last_attempt_ms"
    private const val KEY_FALLBACK_ID = "fallback_device_id"

    private const val OPEN_RETRY_MS = 60L * 60L * 1000L
    private const val CHANGE_RETRY_MS = 60L * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 4_000
                connectTimeoutMillis = 4_000
                socketTimeoutMillis = 4_000
            }
        }
    }

    /** Call from the app's cold-start path; all I/O runs off the main thread. */
    fun pingIfDue(context: Context) {
        if (BuildConfig.DEBUG) return
        if (BuildConfig.ANALYTICS_KEY.isBlank()) return
        if (!BuildConfig.ANALYTICS_URL.startsWith("https://")) return
        if (!running.compareAndSet(false, true)) return

        val app = context.applicationContext
        scope.launch {
            try {
                pingOnce(app)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Analytics must never affect app startup.
            } finally {
                running.set(false)
            }
        }
    }

    private suspend fun pingOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val day = istDay(now)
        val versionCode = AppVersion.code(context)
        val lastDay = prefs.getString(KEY_LAST_DAY, null)
        val lastVersion = prefs.getLong(KEY_LAST_VERSION, -1L)

        val event = when {
            // Existing installs updating into the first analytics build have no
            // baseline here yet; do not count them as fresh installs.
            lastVersion < 0 -> if (AppVersion.isFreshInstall(context)) "install" else "update"
            lastVersion != versionCode -> "update"
            lastDay != day -> "open"
            else -> return
        }

        // A failed open retries after an hour so a backend outage does not turn
        // every cold start into a request. Installs/updates retry sooner.
        val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
        val retryGap = if (event == "open") OPEN_RETRY_MS else CHANGE_RETRY_MS
        if (lastAttempt > 0L && now - lastAttempt < retryGap) return

        val payload = runCatching {
            PingPayload(
                id = deviceHash(context, prefs),
                versionName = AppVersion.name(context),
                versionCode = versionCode,
                flavor = BuildConfig.DISTRIBUTION,
                event = event,
                installer = installerPackage(context),
                osApi = Build.VERSION.SDK_INT,
                osRelease = Build.VERSION.RELEASE.orEmpty(),
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                device = Build.DEVICE.orEmpty(),
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            )
        }.getOrNull() ?: return

        prefs.edit().putLong(KEY_LAST_ATTEMPT, now).apply()

        if (send(payload)) {
            prefs.edit()
                .putString(KEY_LAST_DAY, day)
                .putLong(KEY_LAST_VERSION, versionCode)
                .apply()
        }
    }

    private suspend fun send(payload: PingPayload): Boolean {
        val endpoint = "${BuildConfig.ANALYTICS_URL.trimEnd('/')}/ping"
        return runCatching {
            val response: HttpResponse = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                header("X-App-Key", BuildConfig.ANALYTICS_KEY)
                header("User-Agent", "VIIT-Tracker/${payload.versionName} (${payload.flavor})")
                setBody(json.encodeToString(payload))
            }
            response.status.isSuccess()
        }.getOrDefault(false)
    }

    private fun deviceHash(context: Context, prefs: android.content.SharedPreferences): String {
        val androidId = Settings.Secure
            .getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }

        val stableId = androidId ?: prefs.getString(KEY_FALLBACK_ID, null)
            ?: UUID.randomUUID().toString().also { generated ->
                prefs.edit().putString(KEY_FALLBACK_ID, generated).apply()
            }

        // The salt is app-specific, not secret: it only prevents a raw
        // ANDROID_ID rainbow lookup from being reused across unrelated apps.
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("viit-tracker-analytics-v1:$stableId".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
    }

    private fun installerPackage(context: Context): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager
                .getInstallSourceInfo(context.packageName)
                .installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull().orEmpty()

    private fun istDay(now: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date(now))

    @Serializable
    private data class PingPayload(
        val id: String,
        @SerialName("version_name") val versionName: String,
        @SerialName("version_code") val versionCode: Long,
        val flavor: String,
        val event: String,
        val installer: String,
        @SerialName("os_api") val osApi: Int,
        @SerialName("os_release") val osRelease: String,
        val manufacturer: String,
        val model: String,
        val device: String,
        val abi: String,
    )
}
