package com.vignan.tracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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

    /** Guards the shared-cache observer so one process installs at most one. */
    private val observing = AtomicBoolean(false)

    // ---------------------------------------------------------------- entry points

    /**
     * Push the latest known data into all widget instances and start listening
     * for snapshots fetched by the app. Cache-only: no network.
     *
     * Called from [onUpdate] and from the activity so an app fetch can update
     * widgets that are already on the home screen.
     */
    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val manager = AppWidgetManager.getInstance(appContext)
            val compactIds = manager.getAppWidgetIds(
                ComponentName(appContext, AttendanceAppWidget::class.java)
            )
            if (compactIds.isEmpty()) return@launch

            val data = loadCached(appContext) ?: WidgetData.syncing()
            startObserving(appContext)
            renderAll(appContext, data)
        }
    }

    /**
     * Re-render whenever the app (or another writer) publishes a snapshot.
     * Snapshots sourced from this widget are skipped: the tap/schedule paths
     * already render them with their own transient footer feedback.
     */
    private fun startObserving(context: Context) {
        if (!observing.compareAndSet(false, true)) return

        scope.launch {
            AttendanceCache.snapshot.collect { entry ->
                if (entry == null || entry.source == AttendanceSnapshotSource.WIDGET) {
                    return@collect
                }
                renderAll(context, WidgetData.from(entry.response))
            }
        }

        // Logout/expired session from the app should clear the widget too.
        scope.launch {
            AttendanceCache.invalidations.collect { event ->
                if (event.source != AttendanceSnapshotSource.WIDGET) {
                    renderAll(context, WidgetData.syncing())
                }
            }
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

                val result = AttendanceRepository().fetchLiveAttendance(
                    source = AttendanceSnapshotSource.WIDGET
                )
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
                val result = AttendanceRepository().fetchLiveAttendance(
                    source = AttendanceSnapshotSource.WIDGET
                )
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

        val compactIds = manager.getAppWidgetIds(
            ComponentName(context, AttendanceAppWidget::class.java)
        )
        if (compactIds.isNotEmpty()) {
            val views = RemoteViews(context.packageName, R.layout.widget_attendance)
            views.setOnClickPendingIntent(R.id.widget_container, refreshPendingIntent(context))
            bindData(context, views, data, flash)
            manager.updateAppWidget(compactIds, views)
        }

        return seq
    }

    /** Write a [WidgetData] snapshot into the widget layout. */
    private fun bindData(context: Context, views: RemoteViews, data: WidgetData, flash: WidgetFlash? = null) {
        // A snapshot exists once the portal has held at least one class. While a
        // refresh is in flight we keep those numbers on screen and only change
        // the verdict: blanking them made every tap look like a cold start.
        val hasSnapshot = data.conducted > 0

        bindStatusHeader(
            context = context,
            views = views,
            data = data,
            flash = flash,
            nameId = R.id.widget_student_name,
            rollId = R.id.widget_roll_number,
            pillId = R.id.widget_status_pill,
            detailId = R.id.widget_status_detail
        )

        // Hero percentage — the integer stays white, the fraction takes the
        // verdict colour, matching the dashboard's hero block.
        bindPercentage(
            context = context,
            views = views,
            data = data,
            numberId = R.id.widget_percentage,
            fractionId = R.id.widget_percentage_fraction,
            suffixId = R.id.widget_percentage_suffix
        )
        views.setTextViewText(
            R.id.widget_classes,
            if (hasSnapshot) "${data.attended} / ${data.conducted}" else "— / —"
        )
        views.setTextColor(
            R.id.widget_classes,
            context.getColor(if (hasSnapshot) R.color.text_secondary else R.color.text_muted)
        )

        // Progress rail. The four bars share one value and swap visibility so the
        // fill can change colour without a custom RemoteViews action.
        bindProgress(
            context = context,
            views = views,
            data = data,
            barIds = intArrayOf(
                R.id.widget_progress_safe,
                R.id.widget_progress_warning,
                R.id.widget_progress_danger,
                R.id.widget_progress_neutral
            )
        )

        // The refresh affordance stays constant; the detail line carries feedback.
        views.setTextViewText(R.id.widget_refresh_hint, "↻")
        views.setTextColor(R.id.widget_refresh_hint, context.getColor(R.color.text_muted))

        views.setContentDescription(
            R.id.widget_container,
            if (hasSnapshot) {
                "Attendance ${formatPercentage(data.overallPercentage)}, " +
                    "${data.attended} of ${data.conducted} classes attended. Tap to refresh."
            } else {
                "Attendance is syncing. Tap to refresh."
            }
        )

        // Today chips
        bindTodayChips(context, views, data)
    }

    /** Identity + SAFE/BORDER/DANGER verdict, shared by both widget layouts. */
    private fun bindStatusHeader(
        context: Context,
        views: RemoteViews,
        data: WidgetData,
        flash: WidgetFlash?,
        nameId: Int,
        rollId: Int,
        pillId: Int,
        detailId: Int
    ) {
        val hasSnapshot = data.conducted > 0
        val badge = statusBadge(context, data)

        views.setTextViewText(
            nameId,
            titleCaseName(data.studentName).ifBlank { "Student" }
        )
        views.setTextViewText(rollId, data.rollNumber.ifBlank { "—" })

        views.setTextViewText(pillId, badge.text)
        views.setTextColor(pillId, badge.color)
        views.setInt(pillId, "setBackgroundResource", badge.background)

        // The line under the badge carries the number behind the verdict — how
        // many periods are still safe to skip, or classes needed to recover.
        // After a tap it doubles as the refresh confirmation.
        views.setTextViewText(detailId, flash?.text ?: statusDetail(data, hasSnapshot))
        views.setTextColor(
            detailId,
            context.getColor(
                when {
                    flash == null -> R.color.text_muted
                    flash.ok -> R.color.safe_emerald
                    else -> R.color.danger_rose
                }
            )
        )
    }

    /** Per-subject today chips (up to 3 + "…+N" overflow) or a fallback message. */
    private fun bindTodayChips(
        context: Context,
        views: RemoteViews,
        data: WidgetData,
        chipIds: IntArray = intArrayOf(
            R.id.widget_chip_1,
            R.id.widget_chip_2,
            R.id.widget_chip_3
        ),
        moreId: Int = R.id.widget_chip_more,
        messageId: Int = R.id.widget_today_message
    ) {
        if (data.todayChips.isEmpty()) {
            chipIds.forEach { views.setViewVisibility(it, View.GONE) }
            views.setViewVisibility(moreId, View.GONE)
            views.setTextViewText(
                messageId,
                when {
                    data.isSyncing -> "fetching today…"
                    data.todayNoClasses -> "no classes logged yet"
                    data.todayNoRecords -> "no subject marks yet"
                    else -> "no classes today"
                }
            )
            views.setTextColor(messageId, context.getColor(R.color.text_muted))
            // Previous renders may have hidden the message row (chips were shown) —
            // always restore visibility explicitly; RemoteViews state persists.
            views.setViewVisibility(messageId, View.VISIBLE)
            return
        }

        views.setViewVisibility(messageId, View.GONE)

        data.todayChips.take(chipIds.size).forEachIndexed { index, chip ->
            views.setViewVisibility(chipIds[index], View.VISIBLE)
            views.setTextViewText(chipIds[index], chip.label)
            views.setTextColor(chipIds[index], context.getColor(chipColorRes(chip.tone)))
            views.setInt(chipIds[index], "setBackgroundResource", chipBackgroundRes(chip.tone))
            views.setContentDescription(chipIds[index], chip.contentDescription)
        }
        // Hide any unused chip slots.
        for (i in data.todayChips.size until chipIds.size) {
            views.setViewVisibility(chipIds[i], View.GONE)
        }

        // Overflow: "+N" chip when more subjects than slots.
        val overflow = data.todayChips.size - chipIds.size
        if (overflow > 0) {
            views.setViewVisibility(moreId, View.VISIBLE)
            views.setTextViewText(moreId, "+$overflow")
            views.setTextColor(moreId, context.getColor(R.color.text_muted))
            views.setContentDescription(moreId, "$overflow more subjects")
        } else {
            views.setViewVisibility(moreId, View.GONE)
        }
    }

    /** "NAVEEN KUMAR" / "naveen kumar" → "Naveen Kumar". */
    private fun titleCaseName(raw: String): String =
        raw.trim().split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase() }
        }

    private data class WidgetBadge(
        val text: String,
        val color: Int,
        val background: Int
    )

    private fun AttendanceStatus.colorRes(): Int = when (this) {
        AttendanceStatus.SAFE -> R.color.safe_emerald
        AttendanceStatus.BORDER -> R.color.warning_amber
        AttendanceStatus.DANGER -> R.color.danger_rose
    }

    private fun AttendanceStatus.backgroundRes(): Int = when (this) {
        AttendanceStatus.SAFE -> R.drawable.status_background_safe
        AttendanceStatus.BORDER -> R.drawable.status_background_warning
        AttendanceStatus.DANGER -> R.drawable.status_background_danger
    }

    /** Same SAFE / BORDER / DANGER verdict the app uses. */
    private fun statusBadge(context: Context, data: WidgetData): WidgetBadge {
        if (data.isSyncing || data.conducted <= 0) {
            return WidgetBadge(
                text = "SYNCING",
                color = context.getColor(R.color.text_muted),
                background = R.drawable.status_background_neutral
            )
        }
        val status = attendanceStatus(data.overallPercentage, data.targetPct)
        return WidgetBadge(
            text = status.label,
            color = context.getColor(status.colorRes()),
            background = status.backgroundRes()
        )
    }

    /** The line under the badge: what the verdict means in practice. */
    private fun statusDetail(data: WidgetData, hasSnapshot: Boolean): String {
        if (data.isSyncing) return if (hasSnapshot) "updating…" else "connecting…"
        if (!hasSnapshot) return "no classes yet"

        if (data.overallPercentage >= data.targetPct && data.safeSkipPeriods > 0) {
            return "${data.safeSkipPeriods} period${if (data.safeSkipPeriods == 1) "" else "s"} to skip"
        }
        if (data.classesNeededToRecover > 0) {
            return "need ${data.classesNeededToRecover} class" +
                if (data.classesNeededToRecover == 1) "" else "es"
        }
        if (data.overallPercentage >= data.targetPct) return "no skips left"
        return "below target"
    }

    private fun chipColorRes(tone: WidgetChipTone): Int = when (tone) {
        WidgetChipTone.SAFE -> R.color.safe_emerald
        WidgetChipTone.WARNING -> R.color.warning_amber
        WidgetChipTone.DANGER -> R.color.danger_rose
        WidgetChipTone.NEUTRAL -> R.color.text_muted
    }

    private fun chipBackgroundRes(tone: WidgetChipTone): Int = when (tone) {
        WidgetChipTone.SAFE -> R.drawable.status_background_safe
        WidgetChipTone.WARNING -> R.drawable.status_background_warning
        WidgetChipTone.DANGER -> R.drawable.status_background_danger
        WidgetChipTone.NEUTRAL -> R.drawable.status_background_neutral
    }

    /**
     * Split the hero percentage across three TextViews: white integer, verdict-
     * coloured fraction, muted percent sign. Both widget layouts share it.
     */
    private fun bindPercentage(
        context: Context,
        views: RemoteViews,
        data: WidgetData,
        numberId: Int,
        fractionId: Int,
        suffixId: Int
    ) {
        val hasSnapshot = data.conducted > 0
        if (hasSnapshot) {
            val raw = formatPercentage(data.overallPercentage).removeSuffix("%")
            val fraction = if ('.' in raw) ".${raw.substringAfter('.')}" else ""
            views.setTextViewText(numberId, raw.substringBefore('.'))
            views.setTextViewText(fractionId, fraction)
            views.setViewVisibility(
                fractionId,
                if (fraction.isEmpty()) View.GONE else View.VISIBLE
            )
            views.setTextViewText(suffixId, "%")
            views.setViewVisibility(suffixId, View.VISIBLE)
        } else {
            views.setTextViewText(numberId, "—")
            views.setViewVisibility(fractionId, View.GONE)
            views.setViewVisibility(suffixId, View.GONE)
        }
        views.setTextColor(
            numberId,
            context.getColor(if (hasSnapshot) R.color.text_primary else R.color.text_muted)
        )
        views.setTextColor(
            fractionId,
            statusColor(context, data.overallPercentage, data.targetPct)
        )
        views.setTextColor(suffixId, context.getColor(R.color.text_muted))
    }

    /**
     * Swap one of four pre-coloured bars into view. [barIds] order must be
     * safe, warning, danger, neutral.
     */
    private fun bindProgress(
        context: Context,
        views: RemoteViews,
        data: WidgetData,
        barIds: IntArray
    ) {
        val hasSnapshot = data.conducted > 0
        val progress = data.overallPercentage.roundToInt().coerceIn(0, 100)
        val active = when {
            data.isSyncing || !hasSnapshot -> barIds.last()
            else -> when (attendanceStatus(data.overallPercentage, data.targetPct)) {
                AttendanceStatus.SAFE -> barIds[0]
                AttendanceStatus.BORDER -> barIds[1]
                AttendanceStatus.DANGER -> barIds[2]
            }
        }
        barIds.forEach { id ->
            views.setProgressBar(id, 100, progress, false)
            views.setViewVisibility(id, if (id == active) View.VISIBLE else View.GONE)
        }
    }

    private fun statusColor(
        context: Context,
        percentage: Double,
        targetPct: Double
    ): Int = context.getColor(
        attendanceStatus(percentage, targetPct).colorRes()
    )

    private suspend fun toast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

/** How a subject's chip should read at a glance. */
internal enum class WidgetChipTone { SAFE, WARNING, DANGER, NEUTRAL }

/** One per-subject chip for today's attendance row. */
internal data class WidgetTodayChip(
    val subject: String,
    val present: Int,
    val absent: Int
) {
    /** "3P", "2A", "1P 1A". */
    val countLabel: String
        get() = when {
            present > 0 && absent > 0 -> "${present}P ${absent}A"
            present > 0 -> "${present}P"
            absent > 0 -> "${absent}A"
            else -> "—"
        }

    /** "DL & CO 3P", "DBMS 2A", "MATH 1P 1A". */
    val label: String get() = "$subject $countLabel"

    val tone: WidgetChipTone
        get() = when {
            present > 0 && absent == 0 -> WidgetChipTone.SAFE
            absent > 0 && present == 0 -> WidgetChipTone.DANGER
            present > 0 && absent > 0 -> WidgetChipTone.WARNING
            else -> WidgetChipTone.NEUTRAL
        }

    val contentDescription: String
        get() = buildString {
            append(subject)
            if (present > 0) append(", $present present")
            if (absent > 0) append(", $absent absent")
        }
}

/** Transient status-line confirmation shown in the widget after a tap-to-refresh. */
internal data class WidgetFlash(val text: String, val ok: Boolean)

/** Everything a tap-to-refresh produces: new snapshot, toast text, status-line text. */
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
    /** Target used by the fetch that produced this snapshot. */
    val targetPct: Double = DEFAULT_TARGET_PCT,
    /** Periods still safe to skip without falling under the target. */
    val safeSkipPeriods: Int = 0,
    /** Classes needed to climb back over the target when attendance is short. */
    val classesNeededToRecover: Int = 0,
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
                // Prefer the portal's explicit counts; fall back to the P/A run
                // when an older payload leaves them at zero.
                val run = badge.status.uppercase().replace(" ", "")
                WidgetTodayChip(
                    subject = shortSubjectCode(badge.subject),
                    present = badge.presentCount.takeIf { it > 0 }
                        ?: run.count { it == 'P' },
                    absent = badge.absentCount.takeIf { it > 0 }
                        ?: run.count { it == 'A' }
                )
            }

            return WidgetData(
                studentName = response.profile.name,
                rollNumber = response.profile.rollNo,
                overallPercentage = aggregate.percentage,
                attended = aggregate.attended,
                conducted = aggregate.held,
                targetPct = normalizeTargetPct(response.intelligence.targetPct),
                safeSkipPeriods = skips.periods,
                classesNeededToRecover = skips.classesNeededToRecover,
                todayChips = chips,
                todayNoClasses = todayEntry == null,
                todayNoRecords = todayEntry != null && todayEntry.badges.isEmpty()
            )
        }

        /**
         * "DL & CO - Object Oriented Programming" → "DL & CO",
         * "CS301-Operating Systems" → "CS301".
         */
        private fun shortSubjectCode(raw: String): String =
            raw.substringBefore(" - ")
                .substringBefore('-')
                .trim()
                .uppercase()
                .ifBlank { "SUB" }

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
