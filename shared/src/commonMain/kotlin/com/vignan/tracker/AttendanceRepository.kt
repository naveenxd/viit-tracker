package com.vignan.tracker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AttendanceRepository(
    private val api: ApiClient = ApiClient(),
    private val store: CredentialStore = getCredentialStore(),
) {
    private val loginMutex = Mutex()
    private var loginAttempts = 0

    companion object {
        private const val MAX_ATTEMPTS_PER_HOUR = 10
        private const val TWO_DAYS_MS = 48 * 60 * 60 * 1000L
    }

    suspend fun getStoredCredentials(): StoredCredentials? = store.load()

    fun isSessionValid(creds: StoredCredentials, currentTimeMs: Long): Boolean {
        if (creds.rememberMe) return true
        return (currentTimeMs - creds.lastLoginTimestamp) < TWO_DAYS_MS
    }

    suspend fun getCachedAttendanceResponse(): LiveAttendanceResponse? {
        val json = store.getCachedAttendance() ?: return null
        return runCatching { AppJson.decodeFromString<LiveAttendanceResponse>(json) }.getOrNull()
    }

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
        targetPct: Double = 75.0,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Result<LiveAttendanceResponse> {
        val r = rollNo
        val p = password
        val creds = if (!r.isNullOrBlank() && !p.isNullOrBlank()) {
            StoredCredentials(r.trim(), p, rememberMe, currentTimeMs)
        } else {
            store.load() ?: return Result.failure(IllegalStateException("Not logged in"))
        }

        return try {
            val response = api.liveAttendance(CredentialsRequest(creds.rollNo, creds.password), targetPct)
            // Save valid credentials & cache response on successful fetch
            store.save(creds.copy(lastLoginTimestamp = currentTimeMs))
            store.saveCachedAttendance(AppJson.encodeToString(LiveAttendanceResponse.serializer(), response))
            Result.success(response)
        } catch (e: ApiError.InvalidCredentials) {
            store.clear()
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

    suspend fun logout() = store.clear()
}
