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
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** GitHub repo whose releases drive the in-app updater. */
const val UPDATE_REPO = "naveenxd/viit-tracker"

private const val RELEASES_API =
    "https://api.github.com/repos/$UPDATE_REPO/releases/latest"
private const val USER_AGENT = "viit-tracker-updater"

/** How long to wait before the single retry of a failed check. */
private const val RETRY_DELAY_MS = 600L

/** Name of the checksum manifest published alongside the release APKs. */
private const val CHECKSUMS_FILE = "SHA256SUMS.txt"

/** A release the user can update to. */
data class ReleaseInfo(
    /** Tag as published, e.g. `v1.2.0`. */
    val version: String,
    /** Parsed "what's new" bullets for the update sheet; empty when none. */
    val changelog: List<String>,
    /** Raw release body — shown when no bullets could be parsed. */
    val changelogRaw: String,
    /** Direct URL of the APK chosen for this device, if any. */
    val apkUrl: String?,
    /** File name of the chosen APK — used to find its line in SHA256SUMS.txt. */
    val apkName: String?,
    /** Size of the chosen APK in bytes, when GitHub reports it. */
    val apkSizeBytes: Long?,
    /** URL of the release's SHA256SUMS.txt manifest, when one is published. */
    val checksumsUrl: String?,
    /** HTML release page — fallback when no APK asset is attached. */
    val notesUrl: String,
)

sealed interface UpdateResult {
    /** A newer release exists on GitHub. */
    data class Available(val info: ReleaseInfo) : UpdateResult

    /** Installed build is the latest release (or no releases published yet). */
    data object UpToDate : UpdateResult

    /** Network/API failure — surfaced as a brief "couldn't check" toast. */
    data object CheckFailed : UpdateResult
}

@Serializable
private data class GitHubRelease(
    val tag_name: String = "",
    val html_url: String = "",
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val name: String = "",
    val size: Long = 0,
    val browser_download_url: String = "",
)

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
     * Checks GitHub's latest release against the installed version, retrying
     * once on a transient network failure before giving up.
     */
    suspend fun check(currentVersion: String): UpdateResult {
        repeat(2) { attempt ->
            when (val result = checkOnce(currentVersion)) {
                is UpdateResult.CheckFailed ->
                    if (attempt == 0) delay(RETRY_DELAY_MS) else return result
                else -> return result
            }
        }
        return UpdateResult.CheckFailed
    }

    private suspend fun checkOnce(currentVersion: String): UpdateResult {
        val response = try {
            client.get(RELEASES_API) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return UpdateResult.CheckFailed
        }

        // No releases published yet — nothing to update to.
        if (response.status == HttpStatusCode.NotFound) return UpdateResult.UpToDate
        if (!response.status.isSuccess()) return UpdateResult.CheckFailed

        val release = try {
            response.body<GitHubRelease>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return UpdateResult.CheckFailed
        }

        if (release.draft || release.prerelease) return UpdateResult.UpToDate
        if (!isNewer(release.tag_name, currentVersion)) return UpdateResult.UpToDate

        val apk = pickApkForDevice(release.assets)
        val checksums = release.assets.firstOrNull {
            it.name.equals(CHECKSUMS_FILE, ignoreCase = true)
        }

        return UpdateResult.Available(
            ReleaseInfo(
                version = release.tag_name,
                changelog = parseChangelog(release.body.orEmpty()),
                changelogRaw = release.body?.trim().orEmpty(),
                apkUrl = apk?.browser_download_url,
                apkName = apk?.name,
                apkSizeBytes = apk?.size?.takeIf { it > 0 },
                checksumsUrl = checksums?.browser_download_url,
                notesUrl = release.html_url,
            )
        )
    }

    /**
     * Picks the APK to install, best match first:
     *
     * 1. the split built for one of this device's ABIs (a few times smaller
     *    than the universal build),
     * 2. the universal APK (installs everywhere),
     * 3. any attached APK as a last resort.
     */
    internal fun pickApkForDevice(assets: List<GitHubAsset>): GitHubAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null
        // SUPPORTED_ABIS is ordered most-specific-first (e.g. arm64 before armeabi).
        for (abi in Build.SUPPORTED_ABIS) {
            apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }
        return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: apks.first()
    }

    /**
     * Parses a GitHub release body into "what's new" bullets. Prefers explicit
     * `- ` / `* ` list items; otherwise falls back to non-empty lines with
     * markdown heading markers stripped. Empty when there's nothing to show.
     */
    internal fun parseChangelog(raw: String): List<String> {
        val bullets = raw.lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.substring(2).trim() }
            .filter { it.isNotBlank() }
        if (bullets.isNotEmpty()) return bullets

        return raw.lines()
            .map { it.trim().trimStart('#').trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * True when [remote] (e.g. `v1.2.0`) is a strictly higher version than
     * [local] (e.g. `1.0`, `1.0-debug`). Non-numeric suffixes are ignored so
     * dev/CI builds like `1.0-dev.12` compare sanely.
     */
    internal fun isNewer(remote: String, local: String): Boolean {
        val r = parseVersion(remote)
        val l = parseVersion(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun parseVersion(raw: String): List<Int> =
        raw.removePrefix("v")
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** Installed versionName, read from PackageManager (no BuildConfig needed). */
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
