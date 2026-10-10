package com.vignan.tracker.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Update manifest, served straight from the repo's `main` via
 * raw.githubusercontent.com's CDN — no api.github.com calls, so no rate
 * limits. Maintained by tool/release/generate_version_json.py on every
 * tag release (see .github/workflows/android-release.yml).
 */
private const val MANIFEST_URL =
    "https://raw.githubusercontent.com/naveenxd/viit-tracker/main/version.json"

/** Host of the GitHub releases that carry the APK assets. */
private const val RELEASES_HOST = "https://github.com/naveenxd/viit-tracker"

private const val USER_AGENT = "viit-tracker-updater"

/** How long to wait before the single retry of a failed fetch. */
private const val RETRY_DELAY_MS = 600L

/** A release the user can update to, resolved for this device. */
data class UpdateInfo(
    /** Published version, e.g. `1.2.0` — shown in the sheet. */
    val version: String,
    /** versionCode of the APK built for this device (comparison base). */
    val build: Long,
    /** "What's new" bullets for the update sheet. */
    val changelog: List<String>,
    /** Download URL of the APK for this device's ABI. */
    val apkUrl: String,
    /** Hex SHA-256 the release published for this APK, when known. */
    val apkSha256: String?,
    /** When set, the sheet can't be dismissed (mirai-style forced update). */
    val forceUpdate: Boolean,
    /** HTML release page — fallback when no APK URL is published. */
    val notesUrl: String,
)

sealed interface UpdateResult {
    /** The manifest lists a newer build than the installed one. */
    data class Available(val info: UpdateInfo) : UpdateResult

    /** Installed build is the published latest (or a newer dev build). */
    data object UpToDate : UpdateResult

    /** Network/manifest failure — surfaced as a brief "couldn't check" toast. */
    data object CheckFailed : UpdateResult
}

/**
 * Manifest schema (mirrors mirai's version.json). Unknown keys are ignored
 * so the manifest can grow without breaking shipped clients:
 *
 * ```json
 * {
 *   "latest_version": "1.1.0",
 *   "latest_build": 64,                    // max per-APK versionCode
 *   "apk_build": 63,                       // universal APK's versionCode
 *   "apk_url": ".../viit-tracker-1.1.0.apk",
 *   "apk_urls":  { "arm64-v8a": "...", "armeabi-v7a": "...", "x86_64": "..." },
 *   "apk_builds": { "arm64-v8a": 64, ... },
 *   "sha256":    { "arm64-v8a": "<hex>", ... },
 *   "changelog": ["bullet", "..."],
 *   "force_update": false,
 *   "history": [ { "version": "...", "build": 1, "changelog": [] } ]
 * }
 * ```
 */
@Serializable
internal data class UpdateManifest(
    val latest_version: String = "",
    val latest_build: Long = 0,
    val apk_build: Long = 0,
    val apk_url: String = "",
    val apk_urls: Map<String, String> = emptyMap(),
    val apk_builds: Map<String, Long> = emptyMap(),
    val sha256: Map<String, String> = emptyMap(),
    val changelog: List<String> = emptyList(),
    val force_update: Boolean = false,
)

internal fun UpdateManifest.resolveFor(abi: String?): UpdateInfo {
    // Prefer the split built for this device's primary ABI; fall back to the
    // universal APK (installs everywhere). The sha256 map is keyed the same
    // way — real ABI names plus "universal".
    val useSplit = abi != null && apk_urls.containsKey(abi)
    val urlKey = if (useSplit) abi else null
    return UpdateInfo(
        version = latest_version,
        build = (urlKey?.let { apk_builds[it] } ?: apk_build)
            .takeIf { it > 0 } ?: latest_build,
        changelog = changelog,
        apkUrl = (urlKey?.let { apk_urls[it] }).takeIf { !it.isNullOrBlank() } ?: apk_url,
        apkSha256 = sha256[urlKey ?: "universal"],
        forceUpdate = force_update,
        notesUrl = "$RELEASES_HOST/releases/tag/v$latest_version",
    )
}

object UpdateChecker {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
    }

    /**
     * Fetches the update manifest, retrying once on a transient network
     * failure before giving up.
     */
    suspend fun check(context: Context): UpdateResult {
        repeat(2) { attempt ->
            when (val result = checkOnce(context)) {
                is UpdateResult.CheckFailed ->
                    if (attempt == 0) delay(RETRY_DELAY_MS) else return result
                else -> return result
            }
        }
        return UpdateResult.CheckFailed
    }

    private suspend fun checkOnce(context: Context): UpdateResult {
        val response = try {
            // Cache-bust so the CDN can't hand us a stale manifest after a release.
            client.get("$MANIFEST_URL?_=${System.currentTimeMillis()}") {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.CacheControl, "no-cache")
                header("Pragma", "no-cache")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return UpdateResult.CheckFailed
        }

        if (!response.status.isSuccess()) return UpdateResult.CheckFailed

        val manifest = try {
            response.body<UpdateManifest>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return UpdateResult.CheckFailed
        }

        val abi = Build.SUPPORTED_ABIS.firstOrNull()
        val info = manifest.resolveFor(abi)

        // Missing/blank APK URL → nothing actionable to install.
        if (info.apkUrl.isBlank()) return UpdateResult.CheckFailed
        if (info.build <= currentBuildCode(context)) return UpdateResult.UpToDate

        return UpdateResult.Available(info)
    }

    /**
     * Installed versionCode. The CI release pipeline stamps every split APK
     * with the run number (AGP adds a small per-ABI offset on top), and the
     * manifest publishes each APK's *actual* code — so this comparison is
     * exact per install flavor, no offset math on either side.
     */
    fun currentBuildCode(context: Context): Long {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    /** Installed versionName — display only (badge, "you have X"). */
    fun currentVersionName(context: Context): String {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        return info.versionName ?: "0"
    }
}
