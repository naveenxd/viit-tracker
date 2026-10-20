package com.vignan.tracker

import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar

// Binder call + parsing; cache so FooterBadge's default argument doesn't hit
// PackageManager on every recomposition.
private var cachedAppVersion: String? = null

private fun readAppVersion(): String? {
    val context = AppContextProvider.context ?: return null
    return runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("v")) it else "v$it" }
    }.getOrNull()
}

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    override val appVersion: String
        get() = cachedAppVersion
            ?: readAppVersion()?.also { cachedAppVersion = it }
            ?: "v1.0.0"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getCurrentTimeInfo(): TimeInfo {
    val cal = Calendar.getInstance()
    val dayIdx = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun..6=Sat
    val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    return TimeInfo(dayIdx, mins)
}
