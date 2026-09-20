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
}

expect fun getCredentialStore(): CredentialStore
