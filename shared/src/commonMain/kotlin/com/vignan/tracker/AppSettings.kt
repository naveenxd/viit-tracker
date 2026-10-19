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

    /** Which of the three looks is selected. Defaults to [ThemeMode.SYSTEM]. */
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /**
     * True-black variant, kept separate from [themeMode] so it can be layered
     * on any dark appearance and survive a detour through the light look.
     */
    var amoledDark by mutableStateOf(false)
        private set

    /**
     * The register's RAW matrix: false (default) trails the per-subject totals
     * after the last date, true pins them to the right edge while the dates
     * scroll underneath. Flipped from Settings, not from the matrix itself.
     */
    var registerTotalsPinned by mutableStateOf(false)
        private set

    /**
     * Whether interface motion runs.
     *
     * True by default, and independent of Android's own animator-duration scale:
     * that setting is applied by the platform to every Compose animation clock,
     * so "Remove animations" already freezes the app. This switch is the app's
     * own kill switch on top — it also covers what the system scale cannot,
     * namely the choreography (stagger delays) and the press-scale feedback,
     * both of which would otherwise still run at their full duration.
     */
    var motionEnabled by mutableStateOf(true)
        private set

    /**
     * The dark/light appearance actually showing, once [ThemeMode.SYSTEM] has
     * resolved against [systemDark]. The picker shows the Amoled switch only
     * while this is true — there is nothing to make black otherwise.
     */
    val isDarkNow: Boolean
        get() = when (themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            // Dark, and any stale id that resolved to a dark look.
            else -> true
        }

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
        registerTotalsPinned = store.getRegisterTotalsPinned() ?: false
        store.getMotionEnabled()?.let { motionEnabled = it }
        // Only overwrite when something was actually saved: primeThemeMode may
        // already have restored the values, and the async read must not undo
        // them on the way up.
        applyStoredTheme(store.getThemeMode(), store.getAmoledDark())
    }

    suspend fun setUse24HourClock(enabled: Boolean, store: CredentialStore = getCredentialStore()) {
        use24HourClock = enabled
        store.saveUse24HourClock(enabled)
    }

    suspend fun setThemeMode(mode: ThemeMode, store: CredentialStore = getCredentialStore()) {
        themeMode = mode
        store.saveThemeMode(mode.id)
    }

    suspend fun setAmoledDark(enabled: Boolean, store: CredentialStore = getCredentialStore()) {
        amoledDark = enabled
        store.saveAmoledDark(enabled)
    }

    suspend fun setRegisterTotalsPinned(
        enabled: Boolean,
        store: CredentialStore = getCredentialStore()
    ) {
        registerTotalsPinned = enabled
        store.saveRegisterTotalsPinned(enabled)
    }

    suspend fun setMotionEnabled(enabled: Boolean, store: CredentialStore = getCredentialStore()) {
        motionEnabled = enabled
        store.saveMotionEnabled(enabled)
    }

    /**
     * Restore the saved selection from the unencrypted mirror, synchronously.
     *
     * Called before the first composition: building the encrypted store's master
     * key takes long enough that an async load would paint a frame of the wrong
     * theme first — a black flash for anyone on light mode.
     */
    fun primeThemeMode(store: CredentialStore = getCredentialStore()) {
        applyStoredTheme(store.peekThemeMode(), store.peekAmoledDark())
        // Same reason as the theme: the first composition already decides
        // whether the app's entrance animations run, so read the mirror now.
        store.peekMotionEnabled()?.let { motionEnabled = it }
    }

    /**
     * Seed the state from the persisted pair. Nulls leave the current value
     * alone, so a half-written store never resets the look mid-session.
     */
    private fun applyStoredTheme(storedId: String?, storedAmoled: Boolean?) {
        if (storedId != null) {
            themeMode = ThemeMode.fromId(storedId)
            // "amoled" used to be a mode of its own; it is a modifier of dark now.
            amoledDark = if (storedId == LEGACY_AMOLED_ID) true else storedAmoled ?: amoledDark
        } else {
            storedAmoled?.let { amoledDark = it }
        }
    }

    /** Seeded by the platform before the first composition. See [systemDark]. */
    fun primeSystemDark(isDark: Boolean) {
        systemDark = isDark
    }

    /** Id written by the builds that shipped Amoled as a fourth mode. */
    private const val LEGACY_AMOLED_ID = "amoled"
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
