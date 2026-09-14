package com.vignan.tracker

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val appVersion: String = "v1.0.0"
}

actual fun getPlatform(): Platform = AndroidPlatform()