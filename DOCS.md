# VIIT Attendance API — Kotlin / KMP Client Integration Guide

> A complete, implementable specification for consuming the API securely from a
> Kotlin (Android) or Kotlin Multiplatform (Android + iOS) app. Written so a
> human or an AI agent can follow it top-to-bottom and produce a correct
> implementation.
>
> **Server contract:** see the main [README](../README.md) for endpoint schemas.
> Base URL: `https://viit-attendance-api.<your-subdomain>.workers.dev`

---

## 0. Threat model — what this app actually protects

The API proxies the **student's real portal credentials**. That dictates every
decision below:

| Threat | Consequence | Mitigation (client) |
| --- | --- | --- |
| Credentials in URLs | Passwords land in logs/history/proxies | POST JSON bodies only — the server rejects URL credentials anyway |
| Credentials on disk | Backup/root/jailbreak extraction | Store in `EncryptedSharedPreferences` (Android) / Keychain (iOS) — **never** plain `SharedPreferences`/`NSUserDefaults`, never in files, never in Firebase/crash logs |
| Credentials in memory dumps | Heap snapshots leak them | Fetch → use → clear; don't keep password in a long-lived field |
| Credential stuffing / brute force | Lockouts, abuse of your Worker quota | Respect `429` + `Retry-After` with backoff; cap login attempts client-side |
| MITM | Credential theft in transit | HTTPS enforced by Workers; add certificate pinning (optional, see §8) |
| In-app webviews/screenshots | Password visible on screen | Never render the password; disable screenshots on the login screen |
| Logging | Passwords in logcat/Console | Never log request bodies or password fields |
| Third-party SDKs | Accidental capture | Crash reporters must scrub password fields (see §9) |

**Design rule:** the client is a thin, dumb pipe. All business logic lives on
the Worker. The client holds only what it must: the user's credentials and
their last-known attendance snapshot.

---

## 1. Architecture overview

```
┌────────────────────────────────────────────┐
│ UI (Compose / SwiftUI)                     │
│   LoginScreen  AttendanceScreen  Settings  │
└──────────────┬─────────────────────────────┘
               │ StateFlow / State<UiState>
┌──────────────▼─────────────────────────────┐
│ ViewModels (shared KMP: expected)          │
│   LoginViewModel  AttendanceViewModel      │
└──────────────┬─────────────────────────────┘
               │ suspend functions only
┌──────────────▼─────────────────────────────┐
│ Repository (shared KMP)                    │
│   AuthRepository   AttendanceRepository    │
│   + CredentialStore (expect/actual)        │
│   + ApiClient (Ktor)                       │
└──────────────┬─────────────────────────────┘
               │ HTTPS POST JSON
┌──────────────▼─────────────────────────────┐
│ Cloudflare Worker (this repo)              │
└────────────────────────────────────────────┘
```

Rules for implementers:
- **All networking runs in `suspend` functions** — no callbacks, no
  `runBlocking` on the main thread.
- ViewModels depend only on the repository interface, never on Ktor types.
- The password never leaves the Repository layer.

---

## 2. Dependencies (version catalog)

`gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.1.0"
ktor = "3.0.3"
kotlinxSerialization = "1.7.3"
kotlinxCoroutines = "1.9.0"
securityCrypto = "1.1.0-alpha06"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "securityCrypto" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

Minimum requirements: **Kotlin 2.x**, **Ktor 3.x**, `kotlinx-serialization`
plugin applied. Android `minSdk` must be ≥ 23 for `security-crypto`.

---

## 3. DTOs (shared module)

One serializer set for both platforms. Field names match the server exactly;
`ignoreUnknownKeys` keeps the client forward-compatible when the server adds
fields.

```kotlin
// shared/src/commonMain/kotlin/app/viit/api/Dto.kt
package app.viit.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.json.Json

// ---- requests ----

@Serializable
data class LiveAttendanceRequest(
    val rollNo: String,
    val password: String,
    val targetPct: Double = 75.0,
)

@Serializable
data class CredentialsRequest(
    val rollNo: String,
    val password: String,
)

@Serializable
data class SimulateBunkRequest(
    val currentAttended: Int,
    val currentHeld: Int,
    val selectedPeriodsToday: List<Int>,
    val fullDaysLeave: Int = 0,
    val targetPct: Double = 75.0,
)

// ---- responses ----

@Serializable
data class ErrorResponse(val error: String, val details: String? = null)

@Serializable
data class SubjectAttendance(
    val subject: String,
    val held: Int,
    val attended: Int,
    val percentage: Double,
)

@Serializable
data class ProfileInfo(
    val name: String,
    val rollNo: String,
    val branch: String,
    val semester: String,
    val aggregate: Aggregate,
    val subjects: List<SubjectAttendance>,
)

@Serializable
data class Aggregate(val held: Int, val attended: Int, val percentage: Double)

@Serializable
data class SkipsInfo(
    val periods: Int,
    val days: Int,
    val remainingPeriods: Int,
    val projectedPercentage: Double,
    val status: String,          // "Safe" | "Critical"
    val classesNeededToRecover: Int,
)

@Serializable
data class ForecastDay(
    val date: String,
    val projectedAttended: Int,
    val projectedHeld: Int,
    val projectedPercentage: Double,
)

@Serializable
data class TimetablePeriod(
    val slotNumber: Int,
    val collegePeriod: String,
    val subject: String,
    val time: String,
)

@Serializable
data class TimetableDay(val day: String, val periods: List<TimetablePeriod>)

@Serializable
data class FacultyAllocation(val code: String, val subject: String, val faculty: String)

@Serializable
data class AttendanceSection(
    val subjects: List<SubjectAttendance>,
    val today: List<TodayAttendance>,
    val timetable: List<TimetableDay>,
    val faculty: List<FacultyAllocation>,
)

@Serializable
data class TodayAttendance(val date: String, val badges: List<Badge>)

@Serializable
data class Badge(
    val subject: String,
    val status: String,
    val presentCount: Int,
    val absentCount: Int,
)

@Serializable
data class Intelligence(
    val targetPct: Double,
    val safeSkips: SkipsInfo,
    val forecast7Days: List<ForecastDay>,
)

@Serializable
data class LiveAttendanceResponse(
    val profile: ProfileInfo,
    val attendance: AttendanceSection,
    val intelligence: Intelligence,
    val scrapedAt: String,
)

@Serializable
data class SimulateBunkResponse(
    val current: Snapshot,
    val simulated: Simulated,
    val targetPct: Double,
)

@Serializable
data class Snapshot(val attended: Int, val held: Int, val percentage: Double)

@Serializable
data class Simulated(
    val attended: Int,
    val held: Int,
    val percentage: Double,
    val dropInPercentage: Double,
    val meetsTarget: Boolean,
)

// One shared Json instance — encodeDefaults so targetPct is always sent.
val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = false
}
```

> **Agent note:** if the server adds fields later, this file is the single
> source of truth to update. Do not parse JSON manually anywhere else.

---

## 4. API client (Ktor, shared)

```kotlin
// shared/src/commonMain/kotlin/app/viit/api/ApiClient.kt
package app.viit.api

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

/** Machine-readable failure kinds the UI can branch on. */
sealed class ApiError : Exception() {
    /** Portal rejected the roll number / password. Show "wrong credentials". */
    class InvalidCredentials(val message: String) : ApiError()
    /** Server is being throttled. Retry after [retryAfterSec]. */
    class RateLimited(val retryAfterSec: Int) : ApiError()
    /** Bad request (validation). Usually a client bug — do not retry. */
    class BadRequest(val message: String) : ApiError()
    /** Worker could not reach/reach-parse the portal. Retry with backoff. */
    class Upstream(val message: String) : ApiError()
    /** Network unreachable / timeout / DNS. Retry when connectivity returns. */
    class Network(val cause: Throwable) : ApiError()
    /** Anything else. */
    class Unknown(val code: Int, val body: String) : ApiError()
}

class ApiClient(
    baseUrl: String,
    private val apiKeyProvider: () -> String?,   // read at call time, never stored in a constant
    enableLogging: Boolean = false,              // NEVER true in release builds
) {
    private val base = baseUrl.trimEnd('/')

    private val client = HttpClient {
        expectSuccess = false                    // we map status codes ourselves
        install(ContentNegotiation) { json(AppJson) }
        install(HttpTimeout) {
            // The worker aborts portal fetches at 15 s — give it headroom.
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
        if (enableLogging) {
            // Dev only. Confirm the logger never emits request bodies.
            install(io.ktor.client.plugins.logging.Logging) {
                level = io.ktor.client.plugins.logging.LogLevel.INFO
            }
        }
    }

    private suspend fun post(path: String, body: String): HttpResponse =
        try {
            client.post("$base$path") {
                contentType(ContentType.Application.Json)
                setBody(body)
                apiKeyProvider()?.let { header("X-API-Key", it) }
                // Keep secrets out of any intermediary cache.
                header("Cache-Control", "no-store")
            }
        } catch (e: Throwable) {
            throw ApiError.Network(e)
        }

    private suspend fun mapError(response: HttpResponse): Nothing {
        val body = response.bodyAsText()
        val parsed = runCatching { AppJson.decodeFromString<ErrorResponse>(body) }.getOrNull()
        when (response.status.value) {
            400 -> throw ApiError.BadRequest(parsed?.error ?: "Invalid request")
            401 -> throw ApiError.InvalidCredentials(parsed?.error ?: "Invalid credentials")
            429 -> throw ApiError.RateLimited(
                response.headers["Retry-After"]?.toIntOrNull() ?: 30
            )
            in 500..599 -> throw ApiError.Upstream(parsed?.error ?: "Server error")
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
            LiveAttendanceRequest(creds.rollNo, creds.password, targetPct),
        )
        val response = post("/api/attendance/live", body)
        if (!response.status.isSuccess()) mapError(response)
        return decode(response.bodyAsText())
    }

    /** Cheap credential check before storing anything. */
    suspend fun verifyCredentials(creds: CredentialsRequest) {
        val response = post("/api/auth/verify", AppJson.encodeToString(CredentialsRequest.serializer(), creds))
        if (!response.status.isSuccess()) mapError(response)
    }

    /** Pure math — no credentials needed, safe to call freely. */
    suspend fun simulateBunk(request: SimulateBunkRequest): SimulateBunkResponse {
        val response = post(
            "/api/attendance/simulate-bunk",
            AppJson.encodeToString(SimulateBunkRequest.serializer(), request),
        )
        if (!response.status.isSuccess()) mapError(response)
        return decode(response.bodyAsText())
    }
}
```

---

## 5. Secure credential storage (expect/actual)

Shared interface:

```kotlin
// shared/src/commonMain/kotlin/app/viit/data/CredentialStore.kt
package app.viit.data

/** Immutable snapshot — replace wholesale, never mutate fields in place. */
data class StoredCredentials(val rollNo: String, val password: String)

interface CredentialStore {
    suspend fun load(): StoredCredentials?
    suspend fun save(creds: StoredCredentials)
    suspend fun clear()
}
```

### Android actual — EncryptedSharedPreferences

```kotlin
// shared/src/androidMain/kotlin/app/viit/data/AndroidCredentialStore.kt
package app.viit.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidCredentialStore(context: Context) : CredentialStore {
    // MasterKey lives in the Android Keystore; the prefs file is AES-256 encrypted.
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "viit_secure_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun load(): StoredCredentials? {
        val roll = prefs.getString(KEY_ROLL, null) ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        return StoredCredentials(roll, pass)
    }

    override suspend fun save(creds: StoredCredentials) {
        prefs.edit()
            .putString(KEY_ROLL, creds.rollNo)
            .putString(KEY_PASS, creds.password)
            .apply()
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ROLL = "roll"
        const val KEY_PASS = "pwd"
    }
}
```

### iOS actual — Keychain

```kotlin
// shared/src/iosMain/kotlin/app/viit/data/IosCredentialStore.kt
package app.viit.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.Security.*

class IosCredentialStore : CredentialStore {
    override suspend fun load(): StoredCredentials? = withContext(Dispatchers.Default) {
        val query = baseQuery()
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne
        val item = CFPreferencesGenericValue() // placeholder; use SecItemCopyMatching
        // Implementation: SecItemCopyMatching -> decode kSecValueData -> JSON
        loadFromKeychain()
    }

    private fun baseQuery(): MutableMap<Any, Any> = mutableMapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to "app.viit.credentials",
        kSecAttrAccount to "session",
    )

    private fun loadFromKeychain(): StoredCredentials? {
        val query = baseQuery()
        query[kSecReturnData] = kCFBooleanTrue
        query[kSecMatchLimit] = kSecMatchLimitOne
        val result = kotlinx.cinterop.allocPointerTo<kotlinx.cinterop.COpaqueVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status != errSecSuccess || result.value == null) return null
        val data = result.value!!.readBytes()
        val parts = data.decodeToString().split('\n')
        return if (parts.size == 2) StoredCredentials(parts[0], parts[1]) else null
    }

    override suspend fun save(creds: StoredCredentials) = withContext(Dispatchers.Default) {
        clear()
        val payload = "${creds.rollNo}\n${creds.password}".encodeToByteArray()
        val add = baseQuery()
        add[kSecValueData] = payload.toNSData()
        add[kSecAttrAccessible] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(add, null)
    }

    override suspend fun clear() = withContext(Dispatchers.Default) {
        SecItemDelete(baseQuery())
    }
}

// Extension and imports for NSData conversion are platform boilerplate —
// implement with kotlinx.cinterop / platform.CoreFoundation as usual.
```

> **Agent note:** the iOS snippet sketches the correct flow (generic password
> item, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, delete-then-add). Fill
> in the `NSData` bridging with the standard `ByteArray.toNSData()` helper your
> project already uses, and verify with unit tests on a simulator.

**Storage rules (both platforms):**
- Save credentials **only after** `verifyCredentials` succeeds — never store
  unverified passwords.
- Add `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` (iOS) and exclude from
  Android Auto/backup via `android:allowBackup="false"` or backup rules.
- Offer "Log out" → `CredentialStore.clear()`.

---

## 6. Repository layer (shared)

```kotlin
// shared/src/commonMain/kotlin/app/viit/data/AttendanceRepository.kt
package app.viit.data

import app.viit.api.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AttendanceRepository(
    private val api: ApiClient,
    private val store: CredentialStore,
) {
    // Serialize login attempts: parallel logins burn the rate budget (30/60s).
    private val loginMutex = Mutex()
    private var loginAttempts = 0
    private const val MAX_ATTEMPTS_PER_HOUR = 10

    /** True when credentials verified AND persisted. */
    suspend fun login(rollNo: String, password: String): Result<Unit> {
        val canAttempt = loginMutex.withLock {
            if (loginAttempts >= MAX_ATTEMPTS_PER_HOUR) false
            else { loginAttempts++; true }
        }
        if (!canAttempt) return Result.failure(ApiError.RateLimited(3600))

        val creds = CredentialsRequest(rollNo.trim(), password)
        return try {
            api.verifyCredentials(creds)             // 1. verify first…
            store.save(StoredCredentials(creds.rollNo, creds.password)) // 2. then store
            Result.success(Unit)
        } catch (e: ApiError.InvalidCredentials) {
            // Do NOT store, do NOT clear previous session.
            Result.failure(e)
        }
    }

    suspend fun liveAttendance(targetPct: Double = 75.0): Result<LiveAttendanceResponse> {
        val creds = store.load() ?: return Result.failure(IllegalStateException("Not logged in"))
        return try {
            Result.success(api.liveAttendance(CredentialsRequest(creds.rollNo, creds.password), targetPct))
        } catch (e: ApiError.InvalidCredentials) {
            store.clear()                            // portal rejected a stored session — log out
            Result.failure(e)
        }
    }

    suspend fun logout() = store.clear()
}
```

---

## 7. Rate-limit & retry policy (client side)

The server allows **30 requests / 60 s per IP** and returns `429` with
`Retry-After`. Client policy:

1. **Never auto-retry `400` or `401`.** `400` = client bug; `401` = wrong
   credentials (clear the stored session, show the login screen).
2. **`429`** → surface "Too many attempts, try again in Xs" and disable the
   button for `Retry-After` seconds. No background retries.
3. **`502`/`5xx` + network errors** → exponential backoff, max 3 tries:
   2s → 4s → 8s, jitter ±20%.
4. **Manual refresh only.** Do not poll `/api/attendance/live` on a timer —
   every call performs a real portal login. Refresh on pull-to-refresh or
   screen focus, throttled to once per minute.

```kotlin
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    baseDelayMs: Long = 2_000,
    block: suspend () -> T,
): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: ApiError.Network) {
            if (++attempt >= maxAttempts) throw e
            kotlinx.coroutines.delay(baseDelayMs * (1L shl (attempt - 1)))
        } catch (e: ApiError.Upstream) {
            if (++attempt >= maxAttempts) throw e
            kotlinx.coroutines.delay(baseDelayMs * (1L shl (attempt - 1)))
        }
        // ApiError.BadRequest / InvalidCredentials / RateLimited propagate immediately.
    }
}
```

---

## 8. Certificate pinning (recommended for release)

Workers domains rotate certificates; pin **only if** you serve the API from
your own domain in front of the Worker (e.g. `api.yourapp.in` via a
Cloudflare custom domain you control).

Android (OkHttp `CertificatePinner`), iOS (pin against your domain's
intermediate+leaf SPKI hashes). If you can't control the domain, skip pinning
rather than shipping pins that break when Cloudflare rotates keys — TLS to
`*.workers.dev` is already strongly authenticated.

---

## 9. Android manifest & iOS hygiene

Android `AndroidManifest.xml`:

```xml
<application
    android:usesCleartextTraffic="false"          <!-- HTTPS only -->
    android:allowBackup="false"                    <!-- no credential backup -->
    ... >
```

- Add the login screen to screenshot protection:
  `FLAG_SECURE` in the Activity when the login form is visible.
- Crash reporting (Crashlytics/Bugsnag): add a before-send hook that strips any
  key matching `password|pwd|secret|token` from custom data and breadcrumbs.

iOS:
- `NSAppTransportSecurity` → keep default (ATS blocks HTTP).
- Do not add the password field to autofill password managers unless you
  intend credential persistence there (Keychain-backed autofill is fine).

---

## 10. UI state mapping

```kotlin
sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Success : LoginUiState
    data class Error(val kind: ErrorKind, val message: String) : LoginUiState
}

enum class ErrorKind { INVALID_CREDENTIALS, RATE_LIMITED, NETWORK, SERVER }
```

Mapping table (implement exactly):

| `ApiError` | `ErrorKind` | User message |
| --- | --- | --- |
| `InvalidCredentials` | `INVALID_CREDENTIALS` | "Wrong roll number or password." |
| `RateLimited(r)` | `RATE_LIMITED` | "Too many attempts. Try again in ${r}s." |
| `Network` | `NETWORK` | "You're offline. Check your connection." |
| `Upstream`, `Unknown(5xx)` | `SERVER` | "Attendance service is unavailable. Try again shortly." |
| `BadRequest` | `SERVER` | Same as above (and log a bug — it's a client bug). |

Never render `e.message` raw in production UI — it may contain server details.
Use the table above.

---

## 11. AI-agent implementation checklist

Implement in order; each step depends on the previous:

- [ ] 1. Add version-catalog deps (§2) + `kotlinx-serialization` plugin.
- [ ] 2. Create `Dto.kt` exactly as in §3; write a round-trip serialization test using the sample JSON from the server README.
- [ ] 3. Create `ApiClient.kt` (§4) with `apiKeyProvider` wired from BuildConfig/Info.plist — never hardcoded.
- [ ] 4. Implement `CredentialStore` expect/actual (§5). Android: verify the prefs file on disk is ciphertext. iOS: verify Keychain round-trip on simulator.
- [ ] 5. Implement `AttendanceRepository` (§6) incl. login mutex + attempt cap.
- [ ] 6. Implement `withRetry` (§7) and apply it ONLY to network/upstream paths.
- [ ] 7. Wire UI states (§10); ensure `401` → logout + navigate to login.
- [ ] 8. Android manifest flags + crash-report scrubbing (§9).
- [ ] 9. Release checklist below.

**Definition of done (all must hold):**
- [ ] `grep -r "password"` shows no logging of credential values; no `println`/`Log` of bodies.
- [ ] No credential stored outside `CredentialStore`.
- [ ] `429` is user-visible with countdown; no automatic hammering.
- [ ] `401` clears stored credentials and returns to login.
- [ ] App fails closed: no requests when offline (no retry storm).
- [ ] Release build has `enableLogging = false` and cleartext traffic blocked.

---

## 12. Quick smoke test (curl parity)

After implementing, these client calls must behave identically to:

```sh
# login flow
curl -X POST "$BASE/api/auth/verify" \
  -H 'Content-Type: application/json' -H "X-API-Key: $KEY" \
  -d '{"rollNo":"22B91A05XX","password":"secret"}'

# live data
curl -X POST "$BASE/api/attendance/live" \
  -H 'Content-Type: application/json' -H "X-API-Key: $KEY" \
  -d '{"rollNo":"22B91A05XX","password":"secret","targetPct":75}'

# bunk simulator (no credentials)
curl -X POST "$BASE/api/attendance/simulate-bunk" \
  -H 'Content-Type: application/json' \
  -d '{"currentAttended":300,"currentHeld":400,"selectedPeriodsToday":[1,2,3]}'
```

Expected status codes: `200` on success, `401` for bad credentials,
`400` for malformed bodies, `429` after 30 requests in a minute from one IP,
`502` if the college portal itself is down.
