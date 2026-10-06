package com.vignan.tracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Central widget logic for the attendance widget.
 *
 * - Renders REAL attendance data (cached first, refreshed from the network), including
 *   today's per-subject status chips.
 * - Tap-to-refresh: tapping the widget fires [ACTION_REFRESH] which triggers a network
 *   refetch (no app launch), re-renders the widget and toasts the outcome.
 */
object WidgetUpdater {

    /** Broadcast action sent when the user taps the widget. */
    const val ACTION_REFRESH = "com.vignan.tracker.action.WIDGET_REFRESH"

    /** How long the post-refresh footer confirmation stays on screen. */
    private const val FLASH_HOLD_MS = 3_000L

    /**
     * Toast.show() only *posts* the enqueue to the main looper; if the broadcast's
     * goAsync() finishes before that message runs the process can be torn down and
     * the toast is never handed to the system. Holding the receiver briefly closes
     * the race.
     */
    private const val TOAST_DRAIN_MS = 700L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    /** Monotonic id of the last render — lets a delayed re-render detect staleness. */
    private val renderSeq = AtomicInteger(0)

    // ---------------------------------------------------------------- entry points

    /**
     * Push the latest known data into all widget instances.
     * Uses the encrypted cache — instant, no network. Called from [onUpdate].
     */
    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val data = loadCached(appContext) ?: WidgetData.syncing()
            renderAll(appContext, data)
        }
    }

    /**
     * Handle a user tap on the widget: refetch attendance from the network in the
     * background, re-render with fresh data and toast the outcome.
     *
     * @param onDone called when the work completes — pass
     * [android.content.BroadcastReceiver.PendingResult.finish] from onReceive
     * (via goAsync) so the system keeps the process alive until the fetch finishes.
     */
    fun refreshFromTap(context: Context, onDone: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        scope.launch {
            var locked = false
            try {
                // Serialize: a second tap while a fetch is in flight does nothing
                // (no duplicate toasts / overlapping requests).
                locked = refreshMutex.tryLock()
                if (!locked) return@launch

                // While fetching, show the last known data with a SYNCING state —
                // or a full placeholder when there is nothing cached yet.
                val before = loadCached(appContext) ?: WidgetData.syncing()
                renderAll(appContext, before.asSyncing())

                val result = AttendanceRepository().fetchLiveAttendance()
                val outcome = result.fold(
                    onSuccess = { resp ->
                        WidgetRefreshOutcome(
                            data = WidgetData.from(resp),
                            toast = "Attendance updated",
                            flash = WidgetFlash("updated ✓", ok = true)
                        )
                    },
                    onFailure = { err ->
                        when {
                            // Repository cleared the store on bad credentials —
                            // don't keep showing stale data as if it were valid.
                            err is ApiError.InvalidCredentials ->
                                WidgetRefreshOutcome(
                                    data = WidgetData.syncing(),
                                    toast = "Session expired — log in to refresh",
                                    flash = WidgetFlash("session expired", ok = false)
                                )
                            // Network failed → keep showing last known data.
                            before.isSyncing ->
                                WidgetRefreshOutcome(
                                    data = before,
                                    toast = "Update failed — no cached data",
                                    flash = WidgetFlash("couldn't update", ok = false)
                                )
                            else ->
                                WidgetRefreshOutcome(
                                    data = before,
                                    toast = "Update failed — showing cached data",
                                    flash = WidgetFlash("couldn't update", ok = false)
                                )
                        }
                    }
                )

                // The widget itself carries the confirmation (works even when Android
                // suppresses the background toast), then reverts to its idle footer.
                val seq = renderAll(appContext, outcome.data, outcome.flash)
                scope.launch {
                    delay(FLASH_HOLD_MS)
                    if (renderSeq.get() == seq) renderAll(appContext, outcome.data)
                }

                toast(appContext, outcome.toast)
                // Keep the broadcast alive until the toast has actually been enqueued.
                delay(TOAST_DRAIN_MS)
            } finally {
                if (locked) refreshMutex.unlock()
                onDone?.invoke()
            }
        }
    }

    /**
     * Silent background refresh driven by the widget's hourly update period
     * (`android:updatePeriodMillis`). Same fetch as [refreshFromTap] but with no
     * toast and no "syncing" flicker: the last known snapshot stays on screen until
     * fresh data (or, on failure, the previous snapshot) is ready.
     *
     * @param onDone called when the work completes — pass
     * [android.content.BroadcastReceiver.PendingResult.finish] from onReceive
     * (via goAsync) so the system keeps the process alive until the fetch finishes.
     */
    fun refreshOnSchedule(context: Context, onDone: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        scope.launch {
            var locked = false
            try {
                // Share the lock with tap refreshes so an hourly tick never races a
                // user tap (and vice versa).
                locked = refreshMutex.tryLock()
                if (!locked) return@launch

                val before = loadCached(appContext) ?: WidgetData.syncing()
                val result = AttendanceRepository().fetchLiveAttendance()
                val data = result.fold(
                    onSuccess = { WidgetData.from(it) },
                    onFailure = { err ->
                        // Bad credentials clear the store — don't keep showing stale
                        // data as if it were valid.
                        if (err is ApiError.InvalidCredentials) WidgetData.syncing() else before
                    }
                )
                renderAll(appContext, data)
            } finally {
                if (locked) refreshMutex.unlock()
                onDone?.invoke()
            }
        }
    }

    /** PendingIntent that broadcasts [ACTION_REFRESH] instead of opening the app. */
    private fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AttendanceAppWidget::class.java).apply {
            action = ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ---------------------------------------------------------------- data loading

    private suspend fun loadCached(context: Context): WidgetData? {
        val repository = AttendanceRepository()
        val creds = repository.getStoredCredentials() ?: return null
        if (!repository.isSessionValid(creds, System.currentTimeMillis())) return null
        val cached = repository.getCachedAttendanceResponse() ?: return null
        return WidgetData.from(cached)
    }

    // ---------------------------------------------------------------- rendering

    /**
     * Write a snapshot into every widget instance.
     *
     * @param flash transient footer confirmation ("updated ✓") after a tap refresh.
     * @return the render generation, so callers can tell whether a later render has
     *         superseded this one before scheduling a revert.
     */
    private fun renderAll(context: Context, data: WidgetData, flash: WidgetFlash? = null): Int {
        val seq = renderSeq.incrementAndGet()
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, AttendanceAppWidget::class.java))
        if (ids.isEmpty()) return seq

        val views = RemoteViews(context.packageName, R.layout.widget_attendance)
        views.setOnClickPendingIntent(R.id.widget_container, refreshPendingIntent(context))
        bindData(context, views, data, flash)
        manager.updateAppWidget(ids, views)
        return seq
    }

    /** Write a [WidgetData] snapshot into the widget layout. */
    private fun bindData(context: Context, views: RemoteViews, data: WidgetData, flash: WidgetFlash? = null) {
        val pctColor = statusColor(context, data.overallPercentage)
        val isSafe = !data.isSyncing && data.canSkip
        val isCritical = !data.isSyncing && !data.canSkip
        val accentRes = when {
            data.isSyncing -> R.color.text_muted
            isSafe -> R.color.safe_emerald
            else -> R.color.danger_rose
        }
        val accent = context.getColor(accentRes)
        val pillBg = when {
            data.isSyncing -> R.drawable.status_background_neutral
            isSafe -> R.drawable.status_background_safe
            else -> R.drawable.status_background_danger
        }

        // Header
        views.setTextViewText(
            R.id.widget_student_name,
            data.studentName.uppercase().ifBlank { "STUDENT" }
        )
        views.setTextViewText(R.id.widget_roll_number, data.rollNumber.ifBlank { "—" })

        // Status pill
        views.setTextViewText(
            R.id.widget_status_pill,
            when {
                data.isSyncing -> "SYNCING"
                isSafe -> "CAN SKIP"
                else -> "ATTEND"
            }
        )
        views.setTextColor(R.id.widget_status_pill, accent)
        views.setInt(R.id.widget_status_pill, "setBackgroundResource", pillBg)

        // Main stats
        views.setTextViewText(
            R.id.widget_percentage,
            if (data.isSyncing) "—" else formatPercentage(data.overallPercentage)
        )
        views.setTextColor(R.id.widget_percentage, if (data.isSyncing) accent else pctColor)
        views.setTextViewText(
            R.id.widget_classes,
            if (data.isSyncing) "— / —" else "${data.attended} / ${data.conducted}"
        )

        // Today-row hint — doubles as the tap-to-refresh confirmation.
        // Keep the idle text short: it shares the row with today's subject chips.
        views.setTextViewText(
            R.id.widget_refresh_hint,
            flash?.text ?: "tap ↻"
        )
        views.setTextColor(
            R.id.widget_refresh_hint,
            context.getColor(
                when {
                    flash == null -> R.color.text_muted
                    flash.ok -> R.color.safe_emerald
                    else -> R.color.danger_rose
                }
            )
        )

        // Today chips
        bindTodayChips(context, views, data, accent)
    }

    /** Per-subject today chips (up to 3 + "…+N" overflow) or a fallback message. */
    private fun bindTodayChips(
        context: Context,
        views: RemoteViews,
        data: WidgetData,
        accent: Int
    ) {
        val chipIds = intArrayOf(R.id.widget_chip_1, R.id.widget_chip_2, R.id.widget_chip_3)
        val safeBg = R.drawable.status_background_safe
        val dangerBg = R.drawable.status_background_danger

        if (data.isSyncing || data.todayChips.isEmpty()) {
            chipIds.forEach { views.setViewVisibility(it, View.GONE) }
            views.setViewVisibility(R.id.widget_chip_more, View.GONE)
            views.setTextViewText(
                R.id.widget_today_message,
                when {
                    data.isSyncing -> "fetching today's status…"
                    data.todayNoClasses -> "no classes yet"
                    data.todayNoRecords -> "no attendance recorded yet"
                    else -> "no classes yet"
                }
            )
            views.setTextColor(R.id.widget_today_message, context.getColor(R.color.text_muted))
            // Previous renders may have hidden the message row (chips were shown) —
            // always restore visibility explicitly; RemoteViews state persists.
            views.setViewVisibility(R.id.widget_today_message, View.VISIBLE)
            return
        }

        views.setViewVisibility(R.id.widget_today_message, View.GONE)

        data.todayChips.take(chipIds.size).forEachIndexed { index, chip ->
            val wasAbsent = chip.status.uppercase().contains('A')
            views.setViewVisibility(chipIds[index], View.VISIBLE)
            views.setTextViewText(chipIds[index], "${chip.subject} ${chip.status}")
            views.setTextColor(chipIds[index], accent)
            views.setInt(
                chipIds[index],
                "setBackgroundResource",
                if (wasAbsent) dangerBg else safeBg
            )
        }
        // Hide any unused chip slots.
        for (i in data.todayChips.size until chipIds.size) {
            views.setViewVisibility(chipIds[i], View.GONE)
        }

        // Overflow: "+N" chip when more subjects than slots.
        val overflow = data.todayChips.size - chipIds.size
        if (overflow > 0) {
            views.setViewVisibility(R.id.widget_chip_more, View.VISIBLE)
            views.setTextViewText(R.id.widget_chip_more, "+$overflow")
            views.setTextColor(R.id.widget_chip_more, context.getColor(R.color.text_muted))
        } else {
            views.setViewVisibility(R.id.widget_chip_more, View.GONE)
        }
    }

    private fun statusColor(context: Context, percentage: Double): Int = when {
        percentage >= 80.0 -> context.getColor(R.color.safe_emerald)
        percentage >= 75.0 -> context.getColor(R.color.warning_amber)
        else -> context.getColor(R.color.danger_rose)
    }

    private suspend fun toast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

/** One per-subject chip for today's attendance row. */
internal data class WidgetTodayChip(
    val subject: String,
    val status: String
)

/** Transient footer confirmation shown in the widget after a tap-to-refresh. */
internal data class WidgetFlash(val text: String, val ok: Boolean)

/** Everything a tap-to-refresh produces: new snapshot, toast text, widget footer text. */
private data class WidgetRefreshOutcome(
    val data: WidgetData,
    val toast: String,
    val flash: WidgetFlash?
)

/** Immutable widget snapshot derived from a [LiveAttendanceResponse]. */
internal data class WidgetData(
    val studentName: String = "",
    val rollNumber: String = "",
    val overallPercentage: Double = 0.0,
    val attended: Int = 0,
    val conducted: Int = 0,
    val canSkip: Boolean = false,
    val todayChips: List<WidgetTodayChip> = emptyList(),
    /** No register entry for today at all — there are no classes logged yet. */
    val todayNoClasses: Boolean = false,
    /** Today's entry exists but holds no per-subject badges. */
    val todayNoRecords: Boolean = false,
    val isSyncing: Boolean = false
) {
    fun asSyncing(): WidgetData = copy(isSyncing = true)

    companion object {
        fun syncing() = WidgetData(isSyncing = true)

        fun from(response: LiveAttendanceResponse): WidgetData {
            val skips = response.intelligence.safeSkips
            val aggregate = response.profile.aggregate

            // Today = the register entry matching today's calendar date. An entry whose
            // date can't be parsed is still treated as today (same rule the dashboard
            // uses) — but the LAST entry is never assumed to be today, otherwise a
            // previous day's "present" classes would masquerade as today's schedule.
            val nowMs = System.currentTimeMillis()
            val entries = response.attendance.today
            val todayEntry = entries.firstOrNull { isSameCalendarDay(it.date, nowMs) }
                ?: entries.lastOrNull { parseLooseDate(it.date) == null }
            val chips = todayEntry?.badges.orEmpty().map { badge ->
                WidgetTodayChip(
                    subject = shortSubjectCode(badge.subject),
                    // Portal tokens are P/A runs ("PP", "AA"…) — show as-is.
                    status = badge.status.uppercase().replace(" ", "")
                )
            }

            return WidgetData(
                studentName = response.profile.name,
                rollNumber = response.profile.rollNo,
                overallPercentage = aggregate.percentage,
                attended = aggregate.attended,
                conducted = aggregate.held,
                canSkip = skips.status.equals("Safe", ignoreCase = true),
                todayChips = chips,
                todayNoClasses = todayEntry == null,
                todayNoRecords = todayEntry != null && todayEntry.badges.isEmpty()
            )
        }

        /** "DL & CO - Object Oriented Programming" → "DL & CO". */
        private fun shortSubjectCode(raw: String): String =
            raw.split(" - ").first().trim().uppercase().ifBlank { "SUB" }

        private fun isSameCalendarDay(raw: String?, nowMs: Long): Boolean {
            val epoch = parseLooseDate(raw) ?: return false
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = epoch }
            val now = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
            return cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                    cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
        }

        /** Best-effort parse of the date formats the server may send; epoch ms or null. */
        private fun parseLooseDate(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.trim().uppercase()

            // Register dates are bare "DD/MM" (no year) — anchor to the current year.
            if (cleaned.length == 5 && cleaned[2] == '/') {
                val dd = cleaned.substring(0, 2).toIntOrNull()
                val mm = cleaned.substring(3, 5).toIntOrNull()
                if (dd != null && mm != null && dd in 1..31 && mm in 1..12) {
                    val cal = java.util.Calendar.getInstance()
                    cal.set(java.util.Calendar.MONTH, mm - 1)
                    cal.set(java.util.Calendar.DAY_OF_MONTH, dd)
                    return cal.timeInMillis
                }
            }

            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "dd/MM/yyyy, hh:mm:ss a",
                "dd/MM/yyyy, hh:mm a",
                "dd/MM/yyyy"
            )
            for (pattern in patterns) {
                val parsed = runCatching {
                    val format = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                    format.isLenient = false
                    format.parse(cleaned)
                }.getOrNull()
                if (parsed != null) return parsed.time
            }
            return null
        }
    }
}
