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
}

expect fun getCredentialStore(): CredentialStore
