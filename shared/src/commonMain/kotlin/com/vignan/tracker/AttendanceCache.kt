package com.vignan.tracker

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Which surface produced a snapshot. */
enum class AttendanceSnapshotSource {
    APP,
    WIDGET,

    /** Seeded from the encrypted cache after a process restart. */
    CACHE
}

/** Latest attendance snapshot plus where it came from. */
data class AttendanceSnapshot(
    val response: LiveAttendanceResponse,
    val source: AttendanceSnapshotSource
)

/** An expired session was detected by the app or the widget. */
data class AttendanceInvalidation(
    val source: AttendanceSnapshotSource
)

/**
 * Process-wide source of truth for the latest attendance snapshot.
 *
 * Both the app and the home-screen widget fetch through the same repository,
 * which publishes here after a successful fetch. Persisting to [CredentialStore]
 * keeps the cache across process restarts; the flows let the surface that did
 * *not* fetch react immediately:
 *
 * - widget tap/schedule fetch → app dashboard updates in place
 * - app fetch → widget re-renders with the same data
 *
 * Only one snapshot is retained: attendance is a single logical record, and
 * keeping history here would only invite the two surfaces to disagree.
 */
object AttendanceCache {
    private val mutex = Mutex()

    private val _snapshot = MutableStateFlow<AttendanceSnapshot?>(null)
    val snapshot: StateFlow<AttendanceSnapshot?> = _snapshot.asStateFlow()

    private val _invalidations = MutableSharedFlow<AttendanceInvalidation>(extraBufferCapacity = 1)
    val invalidations: SharedFlow<AttendanceInvalidation> = _invalidations.asSharedFlow()

    /**
     * Read the persisted snapshot once per process and publish it for collectors.
     * The encrypted store is the authority after a kill; the in-memory value wins
     * while the process is alive so both surfaces see the same object.
     */
    suspend fun load(store: CredentialStore = getCredentialStore()): AttendanceSnapshot? =
        mutex.withLock {
            _snapshot.value?.let { return it }

            val json = store.getCachedAttendance() ?: return null
            val response = runCatching {
                AppJson.decodeFromString<LiveAttendanceResponse>(json)
            }.getOrNull() ?: return null

            AttendanceSnapshot(response, AttendanceSnapshotSource.CACHE).also {
                _snapshot.value = it
            }
        }

    /** Persist a fresh snapshot and notify every collector. */
    suspend fun publish(
        response: LiveAttendanceResponse,
        source: AttendanceSnapshotSource,
        store: CredentialStore = getCredentialStore()
    ) {
        mutex.withLock {
            store.saveCachedAttendance(
                AppJson.encodeToString(LiveAttendanceResponse.serializer(), response)
            )
            _snapshot.value = AttendanceSnapshot(response, source)
        }
    }

    /** Drop the in-memory snapshot after the persisted cache was cleared. */
    suspend fun invalidate(source: AttendanceSnapshotSource) {
        mutex.withLock {
            _snapshot.value = null
            _invalidations.tryEmit(AttendanceInvalidation(source))
        }
    }
}
