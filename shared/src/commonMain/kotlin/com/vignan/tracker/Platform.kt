package com.vignan.tracker

interface Platform {
    val name: String
    val appVersion: String
}

data class TimeInfo(
    val dayOfWeekIdx: Int, // 0 = Sun, 1 = Mon ... 6 = Sat
    val minutesOfDay: Int   // hours * 60 + minutes
)

expect fun getPlatform(): Platform
expect fun getCurrentTimeInfo(): TimeInfo