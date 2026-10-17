package com.vignan.tracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide display preferences, persisted through [CredentialStore] so they
 * survive a restart.
 *
 * [use24HourClock] is plain Compose state: anything that reads it (the
 * timetable clock, the "fetched at" stamp, the settings switch) recomposes the
 * moment the user flips the toggle, with no extra plumbing.
 */
object AppSettings {
    var use24HourClock by mutableStateOf(false)
        private set

    suspend fun load(store: CredentialStore = getCredentialStore()) {
        use24HourClock = store.getUse24HourClock() ?: false
    }

    suspend fun setUse24HourClock(enabled: Boolean, store: CredentialStore = getCredentialStore()) {
        use24HourClock = enabled
        store.saveUse24HourClock(enabled)
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
