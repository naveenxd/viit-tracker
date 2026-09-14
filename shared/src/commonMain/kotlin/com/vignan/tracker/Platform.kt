package com.vignan.tracker

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform