package com.vignan.tracker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class AttendanceRepository(
    private val store: CredentialStore = getCredentialStore(),
    private val api: ApiClient = ApiClient(
        baseUrlProvider = { store.getApiBaseUrl() },
        onBaseUrlResolved = { store.saveApiBaseUrl(it) }
    ),
) {
    companion object {
        private const val MAX_ATTEMPTS_PER_HOUR = 10
        private const val ONE_HOUR_MS = 60 * 60 * 1000L
        private const val TWO_DAYS_MS = 48 * 60 * 60 * 1000L

        /**
         * Serializes live fetches across every repository instance in the
         * process. The app and the widget share one portal session; letting
         * both scrape at once can commit an older response after a newer one.
         */
        private val fetchMutex = Mutex()

        /**
         * Failed credential checks are shared process-wide, so creating a new
         * repository (the widget does this per refresh) cannot reset the limit.
         */
        private val loginMutex = Mutex()
        private val loginLimiter = LoginAttemptLimiter(MAX_ATTEMPTS_PER_HOUR, ONE_HOUR_MS)
    }

    /**
     * Stored credentials, or null when there is nothing usable left.
     *
     * A non-remembered session lasts two days. Once it expires the password is
     * removed from disk immediately instead of lingering until the user opens
     * the app and logs in again.
     */
    suspend fun getStoredCredentials(
        currentTimeMs: Long = System.currentTimeMillis()
    ): StoredCredentials? {
        val creds = store.load() ?: return null
        if (isSessionValid(creds, currentTimeMs)) return creds

        clearLocalSessionState()
        AttendanceCache.invalidate(AttendanceSnapshotSource.CACHE)
        return null
    }

    fun isSessionValid(creds: StoredCredentials, currentTimeMs: Long): Boolean {
        if (creds.rememberMe) return true
        return (currentTimeMs - creds.lastLoginTimestamp) < TWO_DAYS_MS
    }

    /**
     * Wipe everything tied to the signed-in student, not only the credentials.
     * Register rows and projection "what if" marks are per-student state and
     * must not leak into the next session.
     */
    private suspend fun clearLocalSessionState() {
        store.clear()
        RegisterCache.clear()
        ProjectionMarks.clear()
    }

    suspend fun getCachedAttendanceResponse(): LiveAttendanceResponse? =
        AttendanceCache.load(store)?.response

    suspend fun getCachedAttendanceReport(query: ReportQuery): AttendanceReportResponse? =
        ResponseCache.loadReport(query, store)

    suspend fun getCachedRegister(): AttendanceRegisterResponse? =
        ResponseCache.loadRegister(store)

    suspend fun getCachedProfile(): ProfileInfo? =
        ResponseCache.loadProfile(store)

    /** True when credentials verified AND persisted. */
    suspend fun login(
        rollNo: String,
        password: String,
        rememberMe: Boolean = true,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Result<Unit> {
        val retryAfter = loginMutex.withLock { loginLimiter.retryAfterSeconds(currentTimeMs) }
        if (retryAfter != null) return Result.failure(ApiError.RateLimited(retryAfter))

        val creds = CredentialsRequest(rollNo.trim(), password)
        return try {
            api.verifyCredentials(creds)
            loginMutex.withLock { loginLimiter.recordSuccess() }
            store.save(StoredCredentials(creds.rollNo, creds.password, rememberMe, currentTimeMs))
            Result.success(Unit)
        } catch (e: ApiError.InvalidCredentials) {
            loginMutex.withLock { loginLimiter.recordFailure(currentTimeMs) }
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
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
        val retryAfter = loginMutex.withLock { loginLimiter.retryAfterSeconds(currentTimeMs) }
        if (retryAfter != null) return@withLock Result.failure(ApiError.RateLimited(retryAfter))

        val r = rollNo
        val p = password
        val creds = if (!r.isNullOrBlank() && !p.isNullOrBlank()) {
            StoredCredentials(r.trim(), p, rememberMe, currentTimeMs)
        } else {
            getStoredCredentials(currentTimeMs)
                ?: return@withLock Result.failure(IllegalStateException("Not logged in"))
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
            loginMutex.withLock { loginLimiter.recordSuccess() }
            // Save valid credentials & publish the one shared snapshot. Publishing
            // persists the cache and wakes the other surface.
            store.save(creds.copy(lastLoginTimestamp = currentTimeMs))
            ResponseCache.saveProfile(response.profile, store)
            AttendanceCache.publish(response, source, store)
            Result.success(response)
        } catch (e: ApiError.InvalidCredentials) {
            loginMutex.withLock { loginLimiter.recordFailure(currentTimeMs) }
            clearLocalSessionState()
            AttendanceCache.invalidate(source)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun simulateBunk(request: SimulateBunkRequest): Result<SimulateBunkResponse> {
        return try {
            Result.success(api.simulateBunk(request))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Portal ATTENDANCE REPORT for a date range. Dates are DD/MM/YYYY strings
     * exactly as the portal expects them; nulls mean "semester to date".
     */
    suspend fun fetchAttendanceReport(
        fromDate: String? = null,
        toDate: String? = null,
        excludeOthers: Boolean = false
    ): Result<AttendanceReportResponse> {
        val creds = getStoredCredentials()
            ?: return Result.failure(IllegalStateException("Not logged in"))
        val query = ReportQuery(fromDate, toDate, excludeOthers)
        return try {
            val response = api.attendanceReport(
                CredentialsRequest(creds.rollNo, creds.password),
                fromDate,
                toDate,
                excludeOthers
            )
            ResponseCache.saveReport(query, response, store)
            Result.success(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /** Full day-by-day academic-register matrix. */
    suspend fun fetchRegister(): Result<AttendanceRegisterResponse> {
        val creds = getStoredCredentials()
            ?: return Result.failure(IllegalStateException("Not logged in"))
        return try {
            val response = api.attendanceRegister(CredentialsRequest(creds.rollNo, creds.password))
            ResponseCache.saveRegister(response, store)
            Result.success(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun fetchProfile(): Result<ProfileInfo> {
        val creds = getStoredCredentials()
            ?: return Result.failure(IllegalStateException("Not logged in"))
        return try {
            val response = api.fetchProfile(CredentialsRequest(creds.rollNo, creds.password))
            ResponseCache.saveProfile(response.profile, store)
            Result.success(response.profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        // Wait for an in-flight fetch first. Otherwise a widget refresh that
        // already read the old credentials can finish after this and publish
        // the logged-out session right back into the cache.
        fetchMutex.withLock {
            clearLocalSessionState()
            AttendanceCache.invalidate(AttendanceSnapshotSource.APP)
        }
    }
}
