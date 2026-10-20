package com.vignan.tracker

interface Platform {
    val name: String
    val appVersion: String
}

/**
 * Which distribution flavor this build is (APK vs Play Store). Set by the
 * Android entry point before the first composition; display-only, never used
 * to decide update behavior (the Gradle flavor already does that at compile
 * time).
 */
object AppDistribution {
    var label: String = "APK"
}

data class TimeInfo(
    val dayOfWeekIdx: Int, // 0 = Sun, 1 = Mon ... 6 = Sat
    val minutesOfDay: Int   // hours * 60 + minutes
)

expect fun getPlatform(): Platform
expect fun getCurrentTimeInfo(): TimeInfo