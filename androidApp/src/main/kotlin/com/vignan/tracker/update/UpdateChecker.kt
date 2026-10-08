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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** GitHub repo whose releases drive the in-app updater. */
const val UPDATE_REPO = "naveenxd/viit-tracker"

private const val RELEASES_API =
    "https://api.github.com/repos/$UPDATE_REPO/releases/latest"
private const val USER_AGENT = "viit-tracker-updater"

/** A release the user can update to. */
data class ReleaseInfo(
    /** Tag as published, e.g. `v1.2.0`. */
    val version: String,
    /** Release body — shown as the changelog in the update popup. */
    val changelog: String,
    /** Direct URL of the preferred APK asset (universal if present), if any. */
    val apkUrl: String?,
    /** HTML release page — fallback when no APK asset is attached. */
    val notesUrl: String,
)

sealed interface UpdateResult {
    /** A newer release exists on GitHub. */
    data class Available(val info: ReleaseInfo) : UpdateResult

    /** Installed build is the latest release (or no releases published yet). */
    data object UpToDate : UpdateResult

    /** Network/API failure — the UI stays silent instead of lying. */
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
private data class GitHubAsset(
    val name: String = "",
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

    /** Checks GitHub's latest release against the installed version. */
    suspend fun check(currentVersion: String): UpdateResult {
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

        val apk = release.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .let { assets ->
                // Universal APK installs on every device; prefer it over ABI splits.
                assets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
                    ?: assets.firstOrNull()
            }

        return UpdateResult.Available(
            ReleaseInfo(
                version = release.tag_name,
                changelog = release.body?.trim().orEmpty(),
                apkUrl = apk?.browser_download_url,
                notesUrl = release.html_url,
            )
        )
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
