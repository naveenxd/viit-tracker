package com.vignan.tracker

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
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
    class ServiceUnavailable(override val message: String) : ApiError()
    class Timeout(override val cause: Throwable) : ApiError()
    class Offline(override val cause: Throwable) : ApiError()
    class Network(override val cause: Throwable) : ApiError()

    class Unknown(val code: Int, val body: String) : ApiError()
}

private val CONNECT_FAILURE_NAMES = setOf(
    "UnknownHostException",
    "ConnectException",
    "NoRouteToHostException",
    "NetworkUnavailableException",
)

/** Maps an engine exception to an [ApiError]; socket failures are name-matched to stay JVM-free. */
internal fun Throwable.toApiError(): ApiError = when (this) {
    is ApiError -> this
    is HttpRequestTimeoutException,
    is ConnectTimeoutException,
    is SocketTimeoutException -> ApiError.Timeout(this)
    else -> {
        val names = generateSequence(this) { it.cause }
            .take(4)
            .mapNotNull { it::class.simpleName }
            .toSet()
        if (names.any { it in CONNECT_FAILURE_NAMES }) ApiError.Offline(this) else ApiError.Network(this)
    }
}

/** The only message the UI shows — never raw engine or upstream text. */
fun Throwable.toUserMessage(): String {
    val raw = message.orEmpty().trim()

    // Portal wording that leaks internals but really means the credentials
    // were rejected ("fmt: no auth token generated").
    if (raw.contains("auth token", ignoreCase = true)) {
        return "Invalid roll number or password."
    }

    return when (this) {
        is ApiError.InvalidCredentials -> "Invalid roll number or password."
        is ApiError.RateLimited -> "Too many attempts. Try again in ${retryAfterSec}s."
        is ApiError.Timeout ->
            "The server is taking longer than usual. Check your connection and try again."
        is ApiError.Offline -> "No connection to the server. Check your network and try again."
        is ApiError.Network -> "Couldn't reach the attendance server. Try again."
        is ApiError.BadRequest -> raw.takeIf { it.isReadableServerMessage() }
            ?: "That request didn't go through. Try again."
        is ApiError.Upstream -> raw.takeIf { it.isReadableServerMessage() }
            ?: "The attendance service is unavailable right now. Try again shortly."
        is ApiError.ServiceUnavailable ->
            "The attendance service is unavailable right now. Try again shortly."
        is ApiError.Unknown -> "The server returned an unexpected response. Try again shortly."
        else -> "Something went wrong. Try again."
    }
}

private fun String.isReadableServerMessage(): Boolean =
    isNotBlank() &&
        length <= 140 &&
        !startsWith("fmt:") &&
        !contains("panic") &&
        !contains("goroutine") &&
        !contains('<') &&
        !contains('\n')

class ApiClient(
    private val kvConfigUrl: String = "https://kv.devh.in/kv/01a0be26-998f-7f10-af66-394511241190/API",
    private val apiKeyProvider: () -> String? = { null },
    /** Persisted lookup consulted before hitting the KV endpoint on cold starts. */
    private val baseUrlProvider: suspend () -> String? = { null },
    /** Called when a KV resolve succeeds, so the URL can be persisted for next time. */
    private val onBaseUrlResolved: suspend (String) -> Unit = {}
) {
    private var cachedBaseUrl: String? = null

    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(AppJson)
        }
        install(HttpTimeout) {
            // The portal scrape often takes >10s on mobile; 10s failed slow-but-working.
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 25_000
            socketTimeoutMillis = 30_000
        }
    }

    private suspend fun getBaseUrl(): String {
        cachedBaseUrl?.let { return it }

        // Skip the KV round trip when a previously resolved URL is stored.
        baseUrlProvider()?.let { stored ->
            if (stored.isNotBlank()) {
                cachedBaseUrl = stored
                return stored
            }
        }

        return try {
            val response = client.get(kvConfigUrl) {
                // Don't let a dead config store add a minute to cold start.
                timeout {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 10_000
                }
            }
            if (response.status.isSuccess()) {
                val rawText = response.bodyAsText().trim()
                
                // 1. Try direct JSON parsing
                val directUrl = runCatching {
                    AppJson.decodeFromString<KvConfigResponse>(rawText).apiUrl
                }.getOrNull()

                // 2. Extract valid https URL cleanly if wrapped inside kv.devh.in envelope metadata
                val extractedUrl = directUrl ?: Regex("""https://[^\s"\\{}]+""").find(rawText)?.value
                
                if (extractedUrl.isNullOrBlank()) {
                    throw ApiError.ServiceUnavailable(
                        "Unable to extract valid API URL from KV response"
                    )
                }

            val cleanUrl = extractedUrl.trimEnd('/')
            cachedBaseUrl = cleanUrl
            onBaseUrlResolved(cleanUrl)
            cleanUrl
            } else {
                throw ApiError.ServiceUnavailable(
                    "Failed to resolve API endpoint from remote KV store"
                )
            }
        } catch (e: ApiError) {
            throw e
        } catch (e: Throwable) {
            throw e.toApiError()
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
            throw e.toApiError()
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
            .getOrElse { throw ApiError.ServiceUnavailable("Malformed response from server") }

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

    /** Portal ATTENDANCE REPORT (ShowAttendance) for an optional date range. */
    suspend fun attendanceReport(creds: CredentialsRequest, fromDate: String? = null, toDate: String? = null, excludeOthers: Boolean = false): AttendanceReportResponse {
        val body = AppJson.encodeToString(
            AttendanceReportRequest.serializer(),
            AttendanceReportRequest(
                rollNo = creds.rollNo,
                password = creds.password,
                fromDate = fromDate?.takeIf { it.isNotBlank() },
                toDate = toDate?.takeIf { it.isNotBlank() },
                excludeOthers = excludeOthers
            )
        )
        val response = post("/api/attendance/report", body)
        if (!response.status.isSuccess()) mapError(response)
        return decode(response.bodyAsText())
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

    /** Full academic-register matrix — the entire day-by-day attendance history. */
    suspend fun attendanceRegister(creds: CredentialsRequest): AttendanceRegisterResponse {
        val response = post(
            "/api/attendance/register",
            AppJson.encodeToString(CredentialsRequest.serializer(), creds)
        )
        if (!response.status.isSuccess()) mapError(response)
        return decode(response.bodyAsText())
    }

    /** Student profile only — fast single-endpoint fetch. */
    suspend fun fetchProfile(creds: CredentialsRequest): ProfileResponse {
        val response = post(
            "/api/profile",
            AppJson.encodeToString(CredentialsRequest.serializer(), creds)
        )
        if (!response.status.isSuccess()) mapError(response)
        return decode(response.bodyAsText())
    }
}
