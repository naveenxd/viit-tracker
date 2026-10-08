package com.vignan.tracker.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object UpdateInstaller {

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 60_000
        }
    }

    /**
     * Streams the release APK into the app's cache, reporting progress in
     * `[0f, 1f]`. Runs on [Dispatchers.IO].
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // drop any partial download

        val target = File(dir, "update.apk")
        client.prepareGet(url) {
            header(HttpHeaders.Accept, "application/octet-stream")
            header(HttpHeaders.UserAgent, "viit-tracker-updater")
        }.execute { response ->
            if (!response.status.isSuccess()) {
                // GitHub asset downloads redirect; a body here explains the failure.
                val detail = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                throw IOException("Download failed (HTTP ${response.status.value}) $detail")
            }
            val total = response.contentLength()?.takeIf { it > 0 } ?: -1L
            val channel = response.bodyAsChannel()
            var received = 0L
            channel.toInputStream().use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        received += read
                        if (total > 0) {
                            onProgress((received.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        if (!target.isFile || target.length() == 0L) {
            throw IOException("Downloaded APK is empty")
        }
        onProgress(1f)
        target
    }

    /**
     * Hands [apkFile] to the system package installer through the FileProvider.
     * Falls back to the release page in the browser if the intent can't be
     * resolved (e.g. install-unknown-apps disabled beyond recovery).
     */
    fun install(context: Context, apkFile: File, fallbackUrl: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openUrl(context, fallbackUrl)
        } catch (_: IllegalArgumentException) {
            // Unknown authority / path — shouldn't happen, but never dead-end the user.
            openUrl(context, fallbackUrl)
        }
    }

    /** Opens [url] externally (browser fallback). */
    fun openUrl(context: Context, url: String) {
        if (url.isBlank()) return
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: ActivityNotFoundException) {
            // No browser available — nothing sensible left to do.
        }
    }
}
