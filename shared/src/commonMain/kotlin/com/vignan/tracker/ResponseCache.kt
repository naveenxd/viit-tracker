package com.vignan.tracker

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

/**
 * Identity of one report request. Reports are cached per range/flag combination
 * so switching back to a month paints instantly instead of re-scraping.
 */
data class ReportQuery(
    val fromDate: String? = null,
    val toDate: String? = null,
    val excludeOthers: Boolean = false
) {
    internal val cacheKey: String
        get() = buildString {
            append(fromDate.orEmpty().replace('/', '-'))
            append('_')
            append(toDate.orEmpty().replace('/', '-'))
            append('_')
            append(if (excludeOthers) '1' else '0')
        }
}

/**
 * Durable cache for endpoint payloads that are not part of the live snapshot.
 *
 * The live snapshot already carries the profile and timetable, and
 * [AttendanceCache] persists that whole payload. These entries cover the two
 * separate portal requests (report and register) plus a profile-only fallback.
 * Everything lives in the same store as credentials, so logout wipes it.
 */
object ResponseCache {
    private const val REPORT_PREFIX = "report_v1_"
    private const val REGISTER_KEY = "register_v1"
    private const val PROFILE_KEY = "profile_v1"

    suspend fun loadReport(
        query: ReportQuery,
        store: CredentialStore = getCredentialStore()
    ): AttendanceReportResponse? =
        read(REPORT_PREFIX + query.cacheKey, AttendanceReportResponse.serializer(), store)

    suspend fun saveReport(
        query: ReportQuery,
        response: AttendanceReportResponse,
        store: CredentialStore = getCredentialStore()
    ) = write(
        REPORT_PREFIX + query.cacheKey,
        AttendanceReportResponse.serializer(),
        response,
        store
    )

    suspend fun loadRegister(
        store: CredentialStore = getCredentialStore()
    ): AttendanceRegisterResponse? =
        read(REGISTER_KEY, AttendanceRegisterResponse.serializer(), store)

    suspend fun saveRegister(
        response: AttendanceRegisterResponse,
        store: CredentialStore = getCredentialStore()
    ) = write(REGISTER_KEY, AttendanceRegisterResponse.serializer(), response, store)

    suspend fun loadProfile(
        store: CredentialStore = getCredentialStore()
    ): ProfileInfo? =
        read(PROFILE_KEY, ProfileInfo.serializer(), store)

    suspend fun saveProfile(
        profile: ProfileInfo,
        store: CredentialStore = getCredentialStore()
    ) = write(PROFILE_KEY, ProfileInfo.serializer(), profile, store)

    private suspend fun <T> read(
        key: String,
        serializer: DeserializationStrategy<T>,
        store: CredentialStore
    ): T? {
        val json = store.getCachedResponse(key) ?: return null
        return runCatching { AppJson.decodeFromString(serializer, json) }.getOrNull()
    }

    private suspend fun <T> write(
        key: String,
        serializer: SerializationStrategy<T>,
        value: T,
        store: CredentialStore
    ) {
        runCatching {
            store.saveCachedResponse(key, AppJson.encodeToString(serializer, value))
        }
    }
}
