package com.vignan.tracker

data class StoredCredentials(val rollNo: String, val password: String)

interface CredentialStore {
    suspend fun load(): StoredCredentials?
    suspend fun save(creds: StoredCredentials)
    suspend fun clear()
}

expect fun getCredentialStore(): CredentialStore
