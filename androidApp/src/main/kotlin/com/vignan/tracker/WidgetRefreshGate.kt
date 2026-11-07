package com.vignan.tracker

import java.util.concurrent.atomic.AtomicBoolean

internal class WidgetRefreshGate {
    private val running = AtomicBoolean(false)
    val isRunning: Boolean get() = running.get()
    fun tryStart(): Boolean = running.compareAndSet(false, true)
    fun finish() { running.set(false) }
}
