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
    }

    suspend fun getStoredCredentials(): StoredCredentials? = store.load()

    /** True when credentials verified AND persisted. */
    suspend fun login(rollNo: String, password: String): Result<Unit> {
        val canAttempt = loginMutex.withLock {
            if (loginAttempts >= MAX_ATTEMPTS_PER_HOUR) false
            else { loginAttempts++; true }
        }
        if (!canAttempt) return Result.failure(ApiError.RateLimited(3600))

        val creds = CredentialsRequest(rollNo.trim(), password)
        return try {
            api.verifyCredentials(creds)
            store.save(StoredCredentials(creds.rollNo, creds.password))
            Result.success(Unit)
        } catch (e: ApiError.InvalidCredentials) {
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun fetchLiveAttendance(rollNo: String? = null, password: String? = null, targetPct: Double = 75.0): Result<LiveAttendanceResponse> {
        val r = rollNo
        val p = password
        val creds = if (!r.isNullOrBlank() && !p.isNullOrBlank()) {
            StoredCredentials(r.trim(), p)
        } else {
            store.load() ?: return Result.failure(IllegalStateException("Not logged in"))
        }

        return try {
            val response = api.liveAttendance(CredentialsRequest(creds.rollNo, creds.password), targetPct)
            // Save valid credentials upon successful live fetch
            store.save(creds)
            Result.success(response)
        } catch (e: ApiError.InvalidCredentials) {
            store.clear()
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun logout() = store.clear()
}
