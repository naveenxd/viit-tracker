package com.vignan.tracker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Installed version identity, shared by analytics and the "What's new" sheet. */
object AppVersion {

    fun name(context: Context): String = runCatching {
        packageInfo(context).versionName?.takeIf { it.isNotBlank() } ?: "0"
    }.getOrDefault("0")

    fun code(context: Context): Long = runCatching {
        val info = packageInfo(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrDefault(0L)

    /**
     * True when this package was installed recently and has never been updated.
     * Used to show "What's new" to existing users updating into the first build
     * that has the sheet, while staying silent on a clean install.
     */
    fun isFreshInstall(context: Context): Boolean = runCatching {
        val info = packageInfo(context)
        info.lastUpdateTime - info.firstInstallTime < 60_000L
    }.getOrDefault(false)

    private fun packageInfo(context: Context): android.content.pm.PackageInfo {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
    }
}
