package com.vignan.tracker

interface Platform {
    val name: String
    val appVersion: String
}

expect fun getPlatform(): Platform