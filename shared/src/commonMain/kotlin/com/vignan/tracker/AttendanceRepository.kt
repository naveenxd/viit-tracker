package com.vignan.tracker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AttendanceRepository(
    private val store: CredentialStore = getCredentialStore(),
    private val api: ApiClient = ApiClient(
        baseUrlProvider = { store.getApiBaseUrl() },
        onBaseUrlResolved = { store.saveApiBaseUrl(it) }
    ),
) {
    private val loginMutex = Mutex()
    private var loginAttempts = 0

    companion object {
        private const val MAX_ATTEMPTS_PER_HOUR = 10
        private const val TWO_DAYS_MS = 48 * 60 * 60 * 1000L

        /**
         * Serializes live fetches across every repository instance in the
         * process. The app and the widget share one portal session; letting
         * both scrape at once can commit an older response after a newer one.
         */
        private val fetchMutex = Mutex()
    }

    suspend fun getStoredCredentials(): StoredCredentials? = store.load()

    fun isSessionValid(creds: StoredCredentials, currentTimeMs: Long): Boolean {
        if (creds.rememberMe) return true
        return (currentTimeMs - creds.lastLoginTimestamp) < TWO_DAYS_MS
    }

    suspend fun getCachedAttendanceResponse(): LiveAttendanceResponse? =
        AttendanceCache.load(store)?.response

    /** True when credentials verified AND persisted. */
    suspend fun login(rollNo: String, password: String, rememberMe: Boolean = true, currentTimeMs: Long = System.currentTimeMillis()): Result<Unit> {
        val canAttempt = loginMutex.withLock {
            if (loginAttempts >= MAX_ATTEMPTS_PER_HOUR) false
            else { loginAttempts++; true }
        }
        if (!canAttempt) return Result.failure(ApiError.RateLimited(3600))

        val creds = CredentialsRequest(rollNo.trim(), password)
        return try {
            api.verifyCredentials(creds)
            store.save(StoredCredentials(creds.rollNo, creds.password, rememberMe, currentTimeMs))
            Result.success(Unit)
        } catch (e: ApiError.InvalidCredentials) {
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun fetchLiveAttendance(
        rollNo: String? = null,
        password: String? = null,
        rememberMe: Boolean = true,
        targetPct: Double? = null,
        currentTimeMs: Long = System.currentTimeMillis(),
        source: AttendanceSnapshotSource = AttendanceSnapshotSource.APP
    ): Result<LiveAttendanceResponse> = fetchMutex.withLock {
        val r = rollNo
        val p = password
        val creds = if (!r.isNullOrBlank() && !p.isNullOrBlank()) {
            StoredCredentials(r.trim(), p, rememberMe, currentTimeMs)
        } else {
            store.load() ?: return@withLock Result.failure(IllegalStateException("Not logged in"))
        }
        // Read the target from the store when the caller did not pin one. This
        // keeps widget refreshes correct even in a fresh process where the
        // in-memory AppSettings object has not been loaded yet.
        val effectiveTarget = normalizeTargetPct(targetPct ?: store.getTargetPct())

        try {
            val response = api.liveAttendance(
                CredentialsRequest(creds.rollNo, creds.password),
                effectiveTarget
            )
            // Save valid credentials & publish the one shared snapshot. Publishing
            // persists the cache and wakes the other surface.
            store.save(creds.copy(lastLoginTimestamp = currentTimeMs))
            AttendanceCache.publish(response, source, store)
            Result.success(response)
        } catch (e: ApiError.InvalidCredentials) {
            store.clear()
            AttendanceCache.invalidate(source)
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun simulateBunk(request: SimulateBunkRequest): Result<SimulateBunkResponse> {
        return try {
            Result.success(api.simulateBunk(request))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Portal ATTENDANCE REPORT for a date range. Dates are DD/MM/YYYY strings
     * exactly as the portal expects them; nulls mean "semester to date".
     */
    suspend fun fetchAttendanceReport(fromDate: String? = null, toDate: String? = null, excludeOthers: Boolean = false): Result<AttendanceReportResponse> {
        val creds = store.load() ?: return Result.failure(IllegalStateException("Not logged in"))
        return try {
            Result.success(api.attendanceReport(CredentialsRequest(creds.rollNo, creds.password), fromDate, toDate, excludeOthers))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /** Full day-by-day academic-register matrix. */
    suspend fun fetchRegister(): Result<AttendanceRegisterResponse> {
        val creds = store.load() ?: return Result.failure(IllegalStateException("Not logged in"))
        return try {
            Result.success(api.attendanceRegister(CredentialsRequest(creds.rollNo, creds.password)))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun fetchProfile(): Result<ProfileInfo> {
        val creds = store.load() ?: return Result.failure(IllegalStateException("Not logged in"))
        return try {
            val response = api.fetchProfile(CredentialsRequest(creds.rollNo, creds.password))
            Result.success(response.profile)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        // Wait for an in-flight fetch first. Otherwise a widget refresh that
        // already read the old credentials can finish after this and publish
        // the logged-out session right back into the cache.
        fetchMutex.withLock {
            store.clear()
            AttendanceCache.invalidate(AttendanceSnapshotSource.APP)
        }
    }
}
