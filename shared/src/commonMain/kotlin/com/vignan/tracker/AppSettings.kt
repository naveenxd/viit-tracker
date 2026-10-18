package com.vignan.tracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide display preferences, persisted through [CredentialStore] so they
 * survive a restart.
 *
 * [use24HourClock] and [themeMode] are plain Compose state: anything that reads
 * them (the timetable clock, the "fetched at" stamp, the settings switch, every
 * colour token in [TrackerColors]) recomposes the moment the user changes them,
 * with no extra plumbing.
 */
object AppSettings {
    var use24HourClock by mutableStateOf(false)
        private set

    /** Which look is in force. Defaults to [ThemeMode.SYSTEM]. */
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /**
     * The device's own dark-mode flag — the input [ThemeMode.SYSTEM] resolves
     * from. [TrackerTheme] refreshes it during composition; [primeSystemDark]
     * sets it before the first frame so a system-dark device never flashes
     * light while the app boots.
     */
    var systemDark by mutableStateOf(false)
        internal set

    suspend fun load(store: CredentialStore = getCredentialStore()) {
        use24HourClock = store.getUse24HourClock() ?: false
        // Only overwrite when a mode was actually saved: [primeThemeMode] may
        // already have restored one from the unencrypted hint, and the async
        // read must not undo it on the way up.
        store.getThemeMode()?.let { themeMode = ThemeMode.fromId(it) }
    }

    suspend fun setUse24HourClock(enabled: Boolean, store: CredentialStore = getCredentialStore()) {
        use24HourClock = enabled
        store.saveUse24HourClock(enabled)
    }

    suspend fun setThemeMode(mode: ThemeMode, store: CredentialStore = getCredentialStore()) {
        themeMode = mode
        store.saveThemeMode(mode.id)
    }

    /**
     * Restore the saved theme from the unencrypted mirror, synchronously.
     *
     * Called before the first composition: building the encrypted store's master
     * key takes long enough that an async load would paint a frame of the wrong
     * theme first — a black flash for anyone on light mode.
     */
    fun primeThemeMode(store: CredentialStore = getCredentialStore()) {
        themeMode = ThemeMode.fromId(store.peekThemeMode())
    }

    /**
     * Seed [systemDark] with the current configuration, before the first
     * composition. Read from the platform side (MainActivity passes the
     * uiMode night flag) because commonMain has no configuration to read.
     */
    fun primeSystemDark(isDark: Boolean) {
        systemDark = isDark
    }
}

/**
 * Minutes-of-day → clock label.
 *
 * 12-hour keeps the college's published convention ("8.45–9.35"); 24-hour is
 * the standard colon form ("08:45–09:35"). Never adds a meridiem, because the
 * portal data never had one.
 */
fun formatClockTime(minutesOfDay: Int, use24Hour: Boolean = AppSettings.use24HourClock): String {
    val h24 = (minutesOfDay / 60) % 24
    val m = minutesOfDay % 60
    return if (use24Hour) {
        "${h24.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    } else {
        val h = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        "$h.${m.toString().padStart(2, '0')}"
    }
}
