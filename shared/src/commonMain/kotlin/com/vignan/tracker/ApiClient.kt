package com.vignan.tracker

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

sealed class ApiError : Exception() {
    class InvalidCredentials(override val message: String) : ApiError()
    class RateLimited(val retryAfterSec: Int) : ApiError()
    class BadRequest(override val message: String) : ApiError()
    class Upstream(override val message: String) : ApiError()
    class Network(override val cause: Throwable) : ApiError()
    class Unknown(val code: Int, val body: String) : ApiError()
}

class ApiClient(
    private val kvConfigUrl: String = "https://kv.devh.in/kv/01a0be26-998f-7f10-af66-394511241190/API",
    private val apiKeyProvider: () -> String? = { null }
) {
    private var cachedBaseUrl: String? = null

    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(AppJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    private suspend fun getBaseUrl(): String {
        cachedBaseUrl?.let { return it }

        return try {
            val response = client.get(kvConfigUrl)
            if (response.status.isSuccess()) {
                val rawText = response.bodyAsText().trim()
                
                // 1. Try direct JSON parsing
                val directUrl = runCatching {
                    AppJson.decodeFromString<KvConfigResponse>(rawText).apiUrl
                }.getOrNull()

                // 2. Extract valid https URL cleanly if wrapped inside kv.devh.in envelope metadata
                val extractedUrl = directUrl ?: Regex("""https://[^\s"\\{}]+""").find(rawText)?.value
                
                if (extractedUrl.isNullOrBlank()) {
                    throw ApiError.Upstream("Unable to extract valid API URL from KV response")
                }

                val cleanUrl = extractedUrl.trimEnd('/')
                cachedBaseUrl = cleanUrl
                cleanUrl
            } else {
                throw ApiError.Upstream("Failed to resolve API endpoint from remote KV store")
            }
        } catch (e: ApiError) {
            throw e
        } catch (e: Throwable) {
            throw ApiError.Network(e)
        }
    }

    private suspend fun post(path: String, body: String): HttpResponse {
        val base = getBaseUrl()
        return try {
            client.post("$base$path") {
                contentType(ContentType.Application.Json)
                setBody(body)
                apiKeyProvider()?.let { header("X-API-Key", it) }
                header("Cache-Control", "no-store")
            }
        } catch (e: ApiError) {
            throw e
        } catch (e: Throwable) {
            throw ApiError.Network(e)
        }
    }

    private suspend fun mapError(response: HttpResponse): Nothing {
        val body = response.bodyAsText()
        val parsed = runCatching { AppJson.decodeFromString<ErrorResponse>(body) }.getOrNull()
        when (response.status.value) {
            400 -> throw ApiError.BadRequest(parsed?.error ?: "Invalid request")
            401 -> throw ApiError.InvalidCredentials(parsed?.error ?: "Invalid roll number or password")
            429 -> {
                val retryAfter = response.headers["Retry-After"]?.toIntOrNull() ?: 30
                throw ApiError.RateLimited(retryAfter)
            }
            in 500..599 -> throw ApiError.Upstream(parsed?.error ?: "Attendance service is currently unavailable")
            else -> throw ApiError.Unknown(response.status.value, body)
        }
    }

    private inline fun <reified T> decode(body: String): T =
        runCatching { AppJson.decodeFromString<T>(body) }
            .getOrElse { throw ApiError.Upstream("Malformed response from server") }

    /** Full attendance payload — the main call. */
    suspend fun liveAttendance(creds: CredentialsRequest, targetPct: Double = 75.0): LiveAttendanceResponse {
        val body = AppJson.encodeToString(
            LiveAttendanceRequest.serializer(),
            LiveAttendanceRequest(creds.rollNo, creds.password, targetPct)
        )
        val response = post("/api/attendance/live", body)
        if (!response.status.isSuccess()) mapError(response)
        return decode(response.bodyAsText())
    }

    /** Credential check before storing anything. */
    suspend fun verifyCredentials(creds: CredentialsRequest) {
        val response = post("/api/auth/verify", AppJson.encodeToString(CredentialsRequest.serializer(), creds))
        if (!response.status.isSuccess()) mapError(response)
    }

    /** Bunk simulator — no credentials needed. */
    suspend fun simulateBunk(request: SimulateBunkRequest): SimulateBunkResponse {
        val response = post(
            "/api/attendance/simulate-bunk",
            AppJson.encodeToString(SimulateBunkRequest.serializer(), request)
        )
        if (!response.status.isSuccess()) mapError(response)
        return decode(response.bodyAsText())
    }
}
