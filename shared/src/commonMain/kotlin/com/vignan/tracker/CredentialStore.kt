package com.vignan.tracker

data class StoredCredentials(
    val rollNo: String,
    val password: String,
    val rememberMe: Boolean = true,
    val lastLoginTimestamp: Long = System.currentTimeMillis()
)

interface CredentialStore {
    suspend fun load(): StoredCredentials?
    suspend fun save(creds: StoredCredentials)
    suspend fun clear()
    suspend fun getCachedAttendance(): String?
    suspend fun saveCachedAttendance(json: String)

    /** Resolved worker base URL, persisted to skip the KV lookup on cold starts. */
    suspend fun getApiBaseUrl(): String?
    suspend fun saveApiBaseUrl(url: String)

    /** Display preference: 24-hour vs the college's 12-hour clock. Null when never chosen. */
    suspend fun getUse24HourClock(): Boolean?
    suspend fun saveUse24HourClock(enabled: Boolean)

    /** Display preference: which of the three themes is active. Null when never chosen. */
    suspend fun getThemeMode(): String?
    suspend fun saveThemeMode(id: String)

    /** Display preference: the true-black variant on top of the dark look. Null when never chosen. */
    suspend fun getAmoledDark(): Boolean?
    suspend fun saveAmoledDark(enabled: Boolean)

    /**
     * Register layout: whether the totals columns are pinned to the right edge.
     * Null when never chosen.
     */
    suspend fun getRegisterTotalsPinned(): Boolean?
    suspend fun saveRegisterTotalsPinned(enabled: Boolean)

    /**
     * Accessibility preference: whether interface motion runs at all. Null when
     * never chosen, which reads as "on" — the app's default.
     */
    suspend fun getMotionEnabled(): Boolean?
    suspend fun saveMotionEnabled(enabled: Boolean)

    /**
     * Non-suspending peek at the theme, from a plain unencrypted mirror of the
     * value above. The theme has to be correct on the very first frame, and
     * opening the encrypted store is far too slow for that.
     */
    fun peekThemeMode(): String?

    /** Non-suspending peek at [getAmoledDark]; same contract as [peekThemeMode]. */
    fun peekAmoledDark(): Boolean?

    /** Non-suspending peek at [getMotionEnabled]; same contract as [peekThemeMode]. */
    fun peekMotionEnabled(): Boolean?
}

expect fun getCredentialStore(): CredentialStore
