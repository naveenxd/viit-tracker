package com.vignan.tracker

internal class WidgetLayoutSize(val width: Int, val height: Int, val fontScale: Float) {
    val showClasses: Boolean get() = width / fontScale >= 220
    val showCaptions: Boolean get() = height / fontScale >= 120
    val showToday: Boolean get() = height / fontScale >= 140
    val showDetails: Boolean get() = width / fontScale >= 250 && height / fontScale >= 120
    val percentageSize: Float get() = (minOf(width / 250f, height / 150f) * 30f / fontScale).coerceIn(24f, 44f)
}
