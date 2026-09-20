package com.vignan.tracker

import android.os.Build
import java.util.Calendar

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val appVersion: String = "v1.0.0"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getCurrentTimeInfo(): TimeInfo {
    val cal = Calendar.getInstance()
    val dayIdx = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun..6=Sat
    val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    return TimeInfo(dayIdx, mins)
}