package com.vignan.tracker

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * "Attendance as per data" projection: for today and each of the next N−1 days, what the
 * overall attendance WILL be at the end of that day if scheduled classes are attended,
 * plus the skippable buffer to stay above 75%.
 *
 * Sundays and days the user marked as holidays are present but greyed out. Supports
 * simulated absences cascading forward.
 */
data class ProjectionRow(
    val dateMs: Long,
    val dateText: String,      // display-ready; formatted once per projection build
    val isSunday: Boolean = false, // true for Sundays (greyed out)
    val isHoliday: Boolean = false, // user-marked: same "no classes" effect as a Sunday
    val classesThatDay: Int,   // classes added to the tally by this day
    val attended: Int,         // cumulative at the END of this day
    val held: Int,
    val percentage: Double,
    val skippable: Int,        // periods that can still be skipped
    val isAbsent: Boolean = false, // user marked the whole day absent in simulation
    val bunkedPeriods: Int = 0 // specific periods picked to bunk today
) {
    /** Day with nothing scheduled: a Sunday, or a day the user marked as holiday. */
    val isOffDay: Boolean get() = isSunday || isHoliday

    /** Some, but not all, of today's classes are marked as skipped. */
    val isPartiallyBunked: Boolean get() = !isAbsent && bunkedPeriods > 0

    /** Any bunk mark at all: full day or selected periods. */
    val isBunked: Boolean get() = isAbsent || isPartiallyBunked
}

/**
 * The user's marks on the projection, shared by the card and the calendar sheets
 * above it, so marking a date in the calendar moves the forecast immediately.
 *
 * Session-scoped on purpose, like the absence simulation it grew out of: these
 * are "what if" marks, not real attendance records.
 *
 * The two sets are mutually exclusive — a day is either bunked (held but not
 * attended) or off (nothing added at all) — because a date cannot be read both
 * ways.
 */
internal object ProjectionMarks {
    /** Left swipe, or a date marked as leave in the calendar. */
    var absentDates by mutableStateOf(setOf<String>())
        private set

    /** Right swipe, or a date marked as a holiday in the calendar. */
    var holidayDates by mutableStateOf(setOf<String>())
        private set

    /**
     * Specific periods picked to bunk today, by period number ([SLOTS]).
     * Session-scoped like the day marks: these shape the forecast, they are not
     * portal attendance records. The set is cleared when the date rolls over.
     */
    var bunkedPeriodsToday by mutableStateOf(setOf<Int>())
        private set

    /** Date the period picks belong to, so an overnight session starts clean. */
    private var periodsDayKey: String? = null

    val isEmpty: Boolean
        get() = absentDates.isEmpty() && holidayDates.isEmpty() && bunkedPeriodsToday.isEmpty()

    fun toggleAbsent(dateKey: String) {
        val wasMarked = dateKey in absentDates
        absentDates = if (wasMarked) absentDates - dateKey else absentDates + dateKey
        holidayDates = holidayDates - dateKey
        // A full-day leave supersedes period picks for that day.
        if (!wasMarked && dateKey == projectionDateKey(System.currentTimeMillis())) {
            bunkedPeriodsToday = emptySet()
        }
    }

    fun toggleHoliday(dateKey: String) {
        val wasMarked = dateKey in holidayDates
        holidayDates = if (wasMarked) holidayDates - dateKey else holidayDates + dateKey
        absentDates = absentDates - dateKey
        if (!wasMarked && dateKey == projectionDateKey(System.currentTimeMillis())) {
            bunkedPeriodsToday = emptySet()
        }
    }

    /** Replace the bunked dates wholesale, from the calendar's Apply. */
    fun setAbsent(dates: Set<String>) {
        absentDates = dates
        holidayDates = holidayDates - dates
        if (projectionDateKey(System.currentTimeMillis()) in dates) {
            bunkedPeriodsToday = emptySet()
        }
    }

    /** Replace the holiday dates wholesale, from the calendar's Apply. */
    fun setHolidays(dates: Set<String>) {
        holidayDates = dates
        absentDates = absentDates - dates
        if (projectionDateKey(System.currentTimeMillis()) in dates) {
            bunkedPeriodsToday = emptySet()
        }
    }

    /** Toggle one of today's periods. Callers gate this on [TodayPeriodSlot.selectable]. */
    fun togglePeriodToday(period: Int) {
        bunkedPeriodsToday = if (period in bunkedPeriodsToday) {
            bunkedPeriodsToday - period
        } else {
            bunkedPeriodsToday + period
        }
    }

    /**
     * Drop picks the portal no longer considers ahead. This is deliberately not
     * time-based: a period picked while it was ahead stays in the forecast until
     * a newer snapshot shows it has already been counted.
     */
    fun retainPeriodsToday(allowed: Set<Int>) {
        val kept = bunkedPeriodsToday intersect allowed
        if (kept != bunkedPeriodsToday) bunkedPeriodsToday = kept
    }

    /** Reset picks when the calendar day changes, without losing them on rotation. */
    fun ensurePeriodsDay(dateKey: String) {
        val known = periodsDayKey
        when {
            known == null -> periodsDayKey = dateKey
            known != dateKey -> {
                periodsDayKey = dateKey
                bunkedPeriodsToday = emptySet()
            }
        }
    }

    fun clear() {
        absentDates = emptySet()
        holidayDates = emptySet()
        bunkedPeriodsToday = emptySet()
    }
}

/** Row/mark key: "d MMM" (e.g. "23 Sep"). Shared by the rows and the mark calendar. */
internal const val PROJECTION_DATE_PATTERN = "d MMM"

/** [PROJECTION_DATE_PATTERN] applied to a millisecond instant, in device-local time. */
internal fun projectionDateKey(millis: Long): String =
    java.text.SimpleDateFormat(PROJECTION_DATE_PATTERN, java.util.Locale.US)
        .format(java.util.Date(millis))

/** (4·a − 3·h) / 3, floored, never negative — buffer to stay exactly at 75%. */
private fun skippablePeriods(attended: Int, held: Int): Int =
    ((4 * attended - 3 * held) / 3).coerceAtLeast(0)

/** Calendar.DAY_OF_WEEK (1=Sun..7=Sat) → weekly-map index (0=Sun..6=Sat). */
private fun dayMapIndex(cal: java.util.Calendar): Int = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1

/**
 * DEBUG ONLY: treat Sunday as a working day so the period picker can be tested
 * on a weekend. Sunday uses its own timetable row when the backend has one,
 * otherwise the first scheduled weekday's periods stand in.
 *
 * Set this back to `false` before shipping.
 */
internal const val DEBUG_PERIOD_BUNK_ON_SUNDAY = false

/** Today's timetable rows, with the debug-only Sunday fallback above. */
private fun todayTimetableItems(
    weekly: Map<Int, List<TTItem>>,
    cal: java.util.Calendar
): List<TTItem> {
    val idx = dayMapIndex(cal)
    if (idx != 0) return weekly[idx].orEmpty()
    if (!DEBUG_PERIOD_BUNK_ON_SUNDAY) return emptyList()
    return weekly[0].orEmpty().ifEmpty {
        weekly.entries
            .filter { it.key != 0 }
            .minByOrNull { it.key }
            ?.value
            .orEmpty()
    }
}

/** Classes for a weekday from the weekly timetable; Sunday/holiday → 0. */
private fun scheduledClasses(weekly: Map<Int, List<TTItem>>, mapIdx: Int): Int =
    weekly[mapIdx].orEmpty().sumOf { it.slots.size }

/** Calendar time → stable "yyyy-MM-dd" key for detecting a day rollover. */
private fun dayKey(time: java.util.Date): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(time)

/** One of today's seven period chips. */
internal data class TodayPeriodSlot(
    val number: Int,
    val selectable: Boolean
)

/**
 * Today's badge rows, matched to the device's current date.
 *
 * The portal may also return an undated row; that is the best fallback when the
 * date format shifts. This mirrors the Today panel's selection so the picker
 * and the panel always talk about the same day.
 */
private fun todayBadgeEntry(
    live: LiveAttendanceResponse,
    nowMillis: Long
): TodayAttendance? {
    val entries = live.attendance.today
    if (entries.isEmpty()) return null
    val parsed = entries.map { it to parseLooseDate(it.date) }
    return parsed.firstOrNull { isSameCalendarDay(it.second, nowMillis) }?.first
        ?: parsed.lastOrNull { it.second == null }?.first
}

/** How many periods a portal badge says were already booked for that subject today. */
private fun Badge.bookedPeriods(): Int {
    val counted = presentCount + absentCount
    if (counted > 0) return counted
    val normalized = status.trim().uppercase()
    return normalized.count { it == 'P' || it == 'A' }
}

/**
 * Completed periods today, straight from the portal's today badges.
 *
 * Deliberately ignores the device clock and timetable: portal writes are often
 * delayed, and a timetable can be cached or wrong. If the portal says six
 * periods were already recorded, the seventh is the next one that can be
 * bunked — regardless of what time it is.
 */
internal fun todayBookedPeriods(
    live: LiveAttendanceResponse,
    nowMillis: Long = System.currentTimeMillis()
): Int {
    val entry = todayBadgeEntry(live, nowMillis) ?: return 0
    return entry.badges.sumOf { it.bookedPeriods() }.coerceIn(0, SLOTS.size)
}

/**
 * Chips for today. Eligibility is portal-driven only: periods after the number
 * already booked are open, everything before is done. The timetable is not
 * consulted here.
 */
internal fun todayPeriodSlots(
    live: LiveAttendanceResponse?,
    nowMillis: Long = System.currentTimeMillis(),
    blocked: Boolean = false
): List<TodayPeriodSlot> {
    if (live == null) {
        return SLOTS.map { TodayPeriodSlot(number = it.n, selectable = false) }
    }
    val booked = todayBookedPeriods(live, nowMillis)
    return SLOTS.map { slot ->
        TodayPeriodSlot(
            number = slot.n,
            selectable = !blocked && slot.n > booked
        )
    }
}

/**
 * Scheduled slots the projection still has to add for today: timetable slots
 * after the portal-booked count, plus any period the user explicitly picked
 * that the timetable does not list. The timetable defines which slots exist;
 * the portal decides how far down that list is already accounted for.
 */
private fun remainingTodaySlots(
    live: LiveAttendanceResponse,
    cal: java.util.Calendar,
    picked: Set<Int> = emptySet()
): List<Int> {
    val booked = todayBookedPeriods(live, cal.timeInMillis)
    val weekly = buildWeeklyTimetable(live.attendance.timetable, live.attendance.faculty)
    val scheduled = todayTimetableItems(weekly, cal)
        .flatMap { it.slots }
        .filter { it > booked }
    // Picks are an explicit "this class exists" from the user; honour them even
    // when the cached timetable is missing the slot.
    val explicit = picked.filter { it > booked }
    return (scheduled + explicit).distinct().sorted()
}

/**
 * Period numbers still open today: everything after the portal's booked count.
 * Deliberately timetable-free, matching the chips and the pruning rule.
 */
internal fun todayRemainingPeriods(
    live: LiveAttendanceResponse?,
    nowMillis: Long = System.currentTimeMillis()
): Set<Int> {
    if (live == null) return emptySet()
    val booked = todayBookedPeriods(live, nowMillis)
    return SLOTS.map { it.n }.filter { it > booked }.toSet()
}

/** End-of-today attendance if the picked periods are bunked and the rest attended. */
internal data class TodayBunkSummary(
    val remainingPeriods: Int,
    val bunkedPeriods: Int,
    val attended: Int,
    val held: Int,
    val percentage: Double
)

/**
 * The same arithmetic as projection row 0, exposed so the home hero can show
 * what today's period picks would do to the overall percentage without
 * duplicating (and eventually drifting from) the forecast.
 */
internal fun todayBunkSummary(
    live: LiveAttendanceResponse,
    bunkedPeriods: Set<Int>,
    nowMillis: Long = System.currentTimeMillis(),
    blocked: Boolean = false
): TodayBunkSummary? {
    val agg = live.profile.aggregate
    if (agg.held <= 0 || blocked) return null

    val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
    if (cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY &&
        !DEBUG_PERIOD_BUNK_ON_SUNDAY
    ) return null

    val remaining = remainingTodaySlots(live, cal, bunkedPeriods)
    val picked = remaining.count { it in bunkedPeriods }
    val attended = agg.attended + remaining.size - picked
    val held = agg.held + remaining.size

    return TodayBunkSummary(
        remainingPeriods = remaining.size,
        bunkedPeriods = picked,
        attended = attended,
        held = held,
        percentage = if (held > 0) attended * 100.0 / held else 0.0
    )
}

/**
 * Builds the attendance projection list for the given number of days.
 * - Row 0 is TODAY at end-of-day: the portal snapshot plus the timetable slots
 *   after the periods the portal has already booked (and any explicitly picked
 *   period the timetable omitted) — attended, or missed if today is in
 *   [absentDates] or if individual periods are in [bunkedPeriodsToday].
 *   Completed periods are counted from today's portal badges, not from the
 *   device clock.
 * - Future days (starting tomorrow) project attendance assuming scheduled classes are attended,
 *   or missed if included in [absentDates].
 * - Sundays, and any day in [holidayDates], are present in sequence but marked as off days:
 *   0 classes, percentages carried forward, greyed out. A holiday is the user saying the
 *   college does not run that day, so nothing is added to either tally — unlike an absence,
 *   which still counts the classes as held. When a date is in both sets the holiday wins.
 */
fun buildAttendanceProjection(
    live: LiveAttendanceResponse,
    days: Int,
    absentDates: Set<String> = emptySet(),
    holidayDates: Set<String> = emptySet(),
    bunkedPeriodsToday: Set<Int> = emptySet()
): List<ProjectionRow> {
    val agg = live.profile.aggregate
    if (agg.held <= 0) return emptyList()

    val weekly = buildWeeklyTimetable(live.attendance.timetable, live.attendance.faculty)
    val cal = java.util.Calendar.getInstance()
    // "d MMM" (e.g. "23 Sep") keeps dates compact and guarantees room for labels without clipping
    val dateFormat = java.text.SimpleDateFormat(PROJECTION_DATE_PATTERN, java.util.Locale.US)

    var attended = agg.attended
    var held = agg.held
    val rows = mutableListOf<ProjectionRow>()

    // Row 0 — TODAY at end-of-day: snapshot + the classes still ahead of the scrape
    val isTodaySunday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY &&
        !DEBUG_PERIOD_BUNK_ON_SUNDAY
    val todayDateText = dateFormat.format(java.util.Date(cal.timeInMillis))
    val isTodayHoliday = !isTodaySunday && todayDateText in holidayDates

    if (isTodaySunday || isTodayHoliday) {
        rows += ProjectionRow(
            dateMs = cal.timeInMillis,
            dateText = todayDateText,
            isSunday = isTodaySunday,
            isHoliday = isTodayHoliday,
            classesThatDay = 0,
            attended = attended,
            held = held,
            percentage = if (held > 0) attended * 100.0 / held else 0.0,
            skippable = skippablePeriods(attended, held),
            isAbsent = false
        )
    } else {
        val remainingSlots = remainingTodaySlots(live, cal, bunkedPeriodsToday)
        val todayClasses = remainingSlots.size
        val isTodayAbsent = todayDateText in absentDates
        val todayBunks = if (isTodayAbsent) {
            0
        } else {
            remainingSlots.count { it in bunkedPeriodsToday }
        }

        if (isTodayAbsent) {
            // Absent/Bunked: classes held increases, but attended does not!
            held += todayClasses
        } else {
            // Only the picked periods are missed; the rest are assumed attended.
            attended += todayClasses - todayBunks
            held += todayClasses
        }

        rows += ProjectionRow(
            dateMs = cal.timeInMillis,
            dateText = todayDateText,
            isSunday = false,
            classesThatDay = todayClasses,
            attended = attended,
            held = held,
            percentage = if (held > 0) attended * 100.0 / held else 0.0,
            skippable = skippablePeriods(attended, held),
            isAbsent = isTodayAbsent,
            bunkedPeriods = todayBunks
        )
    }

    // Future days: iterate starting tomorrow until we reach requested count of days
    while (rows.size < days) {
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)

        val isSunday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
        val dateText = dateFormat.format(java.util.Date(cal.timeInMillis))
        val isHoliday = !isSunday && dateText in holidayDates

        if (isSunday || isHoliday) {
            // Off day: greyed out, 0 classes scheduled, stats carry forward from the
            // previous day. Same treatment either way — a holiday is the user telling
            // the projection the college is shut, so no classes are added at all.
            rows += ProjectionRow(
                dateMs = cal.timeInMillis,
                dateText = dateText,
                isSunday = isSunday,
                isHoliday = isHoliday,
                classesThatDay = 0,
                attended = attended,
                held = held,
                percentage = if (held > 0) attended * 100.0 / held else 0.0,
                skippable = skippablePeriods(attended, held),
                isAbsent = false
            )
        } else {
            val classes = scheduledClasses(weekly, dayMapIndex(cal))
            val isAbsent = dateText in absentDates

            if (isAbsent) {
                // Absent/Bunked: classes held increases, but attended does not!
                held += classes
            } else {
                // Attended: both increase
                attended += classes
                held += classes
            }

            rows += ProjectionRow(
                dateMs = cal.timeInMillis,
                dateText = dateText,
                isSunday = false,
                classesThatDay = classes,
                attended = attended,
                held = held,
                percentage = if (held > 0) attended * 100.0 / held else 0.0,
                skippable = skippablePeriods(attended, held),
                isAbsent = isAbsent
            )
        }
    }
    return rows
}

// ---------------------------------------------------------------- card

private val PROJECTION_RANGES = listOf(10, 30, 60)

@Composable
fun ProjectionCard(
    liveResponse: LiveAttendanceResponse?,
    hasData: Boolean,
    modifier: Modifier = Modifier
) {
    var range by remember { mutableStateOf(10) }
    var showHelp by remember { mutableStateOf(false) }
    // The marks live in ProjectionMarks so the calendar sheets above the card
    // write straight into what the forecast reads.
    val absentDates = ProjectionMarks.absentDates
    val holidayDates = ProjectionMarks.holidayDates
    val bunkedPeriodsToday = ProjectionMarks.bunkedPeriodsToday
    // Recompute the projection when the calendar day rolls over, so the "today"
    // row and its remaining classes stay correct without a refetch.
    var dayStamp by remember { mutableStateOf(dayKey(java.util.Calendar.getInstance().time)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val now = dayKey(java.util.Calendar.getInstance().time)
            if (now != dayStamp) {
                dayStamp = now
                break
            }
        }
    }

    val rows = remember(liveResponse, range, absentDates, holidayDates, bunkedPeriodsToday, dayStamp) {
        liveResponse?.let {
            buildAttendanceProjection(it, range, absentDates, holidayDates, bunkedPeriodsToday)
        } ?: emptyList()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            // Header in two shared rows: the name and its "i" sit with the range
            // control up top, the hint (or the mark count) shares the second row
            // with Reset. Stacking the icon over the chips made the right side a
            // row taller than the left, which left the hint sitting in dead space.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ATTENDANCE PROJECTION",
                        color = TrackerColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    ProjectionInfoButton(onClick = { showHelp = true })
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PROJECTION_RANGES.forEach { r ->
                        RangeChip(
                            label = "${r}D",
                            selected = range == r,
                            onClick = { range = r }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        absentDates.isEmpty() && holidayDates.isEmpty() && bunkedPeriodsToday.isEmpty() ->
                            "Swipe left to bunk, right for holiday"
                        else -> buildString {
                            if (bunkedPeriodsToday.isNotEmpty()) {
                                append("${bunkedPeriodsToday.size} period")
                                if (bunkedPeriodsToday.size != 1) append("s")
                                append(" today")
                            }
                            if (absentDates.isNotEmpty()) {
                                if (isNotEmpty()) append(" · ")
                                append("${absentDates.size} bunked")
                            }
                            if (holidayDates.isNotEmpty()) {
                                if (isNotEmpty()) append(" · ")
                                append("${holidayDates.size} holiday")
                            }
                        }
                    },
                    color = when {
                        absentDates.isNotEmpty() || bunkedPeriodsToday.isNotEmpty() -> TrackerColors.DangerRose
                        holidayDates.isNotEmpty() -> TrackerColors.WarningAmber
                        else -> TrackerColors.TextSubtle
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (!ProjectionMarks.isEmpty) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TrackerColors.DangerRoseSubtle)
                            .pressClickable { ProjectionMarks.clear() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "RESET",
                            color = TrackerColors.DangerRose,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(11.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TrackerColors.HairlineBorder)
            )
            Spacer(modifier = Modifier.height(6.dp))

            when {
                !hasData -> Text(
                    text = "Fetch attendance to project the next $range days.",
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                rows.isEmpty() -> Text(
                    text = "Not enough data to project yet.",
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                else -> {
                    // Table Column Headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DATE",
                            color = TrackerColors.TextSubtle,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "PROJECTED",
                            color = TrackerColors.TextSubtle,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1.0f)
                        )
                        Text(
                            text = "CLASSES",
                            color = TrackerColors.TextSubtle,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BUFFER",
                            color = TrackerColors.TextSubtle,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        rows.forEachIndexed { index, row ->
                            ProjectionLine(
                                row = row,
                                isToday = index == 0,
                                onSwipeBunk = { ProjectionMarks.toggleAbsent(row.dateText) },
                                onSwipeHoliday = { ProjectionMarks.toggleHoliday(row.dateText) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHelp) {
        ProjectionHelpSheet(onDismiss = { showHelp = false })
    }
}

/**
 * Info affordance beside the card's name: a drawn circled "i" at the app's glyph
 * weight. The 24dp target keeps it no taller than the range chips it shares a
 * row with, so the header stays two tight rows.
 */
@Composable
private fun ProjectionInfoButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .pressClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(15.dp)) {
            val sw = 1.4.dp.toPx()
            val w = size.width
            val h = size.height

            drawCircle(
                color = TrackerColors.TextMuted,
                radius = (size.minDimension - sw) / 2f,
                style = Stroke(width = sw)
            )
            // Stem and dot, sized off the stroke so the mark stays optically even
            // with the ring at any size.
            drawLine(
                color = TrackerColors.TextMuted,
                start = Offset(w * 0.5f, h * 0.46f),
                end = Offset(w * 0.5f, h * 0.74f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = TrackerColors.TextMuted,
                radius = sw * 0.65f,
                center = Offset(w * 0.5f, h * 0.29f)
            )
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) TrackerColors.SurfaceElevated else Color.Transparent)
            .border(
                1.dp,
                if (selected) TrackerColors.HairlineBorderLight else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .pressClickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (selected) TrackerColors.PrimaryWhite else TrackerColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ProjectionLine(
    row: ProjectionRow,
    isToday: Boolean,
    onSwipeBunk: () -> Unit,
    onSwipeHoliday: () -> Unit
) {
    var dragOffset by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dragOffset"
    )

    val rowBackground = when {
        row.isBunked -> TrackerColors.DangerRoseSubtle
        isToday -> TrackerColors.SurfaceElevated.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val rowBorder = when {
        row.isBunked -> TrackerColors.DangerRose.copy(alpha = 0.3f)
        isToday -> TrackerColors.HairlineBorderLight
        else -> Color.Transparent
    }

    // High contrast threshold: always green for >= 75%
    val pctColor = when {
        row.isOffDay -> TrackerColors.TextSubtle
        row.isAbsent -> TrackerColors.DangerRose
        row.percentage >= 75.0 -> TrackerColors.SafeEmerald
        row.percentage >= 70.0 -> TrackerColors.WarningAmber
        else -> TrackerColors.DangerRose
    }

    // Sundays are certainly off, so they recede furthest. A marked holiday is the
    // user's own call and stays a little more legible. Applied to the row's content
    // only: the row itself must stay opaque, or the drag reveal underneath would
    // show through its text.
    val rowAlpha = when {
        row.isSunday -> 0.35f
        row.isHoliday -> 0.6f
        else -> 1f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
    ) {
        // Drag previews: left swipe offers BUNK on the right edge, right swipe offers
        // HOLIDAY on the left. An already-marked day offers the way back instead
        // ("RESTORE" / "CLEAR"), so a swipe never silently re-marks what is set.
        if (!row.isSunday && dragOffset < -8f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (row.isAbsent) TrackerColors.SurfaceElevated else TrackerColors.DangerRoseSubtle)
                    .padding(end = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = if (row.isAbsent) "RESTORE" else "BUNK",
                    color = if (row.isAbsent) TrackerColors.TextSecondary else TrackerColors.DangerRose,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        if (!row.isSunday && dragOffset > 8f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (row.isHoliday) TrackerColors.SurfaceInput else TrackerColors.SurfaceElevated
                    )
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (row.isHoliday) "CLEAR" else "HOLIDAY",
                    color = if (row.isHoliday) TrackerColors.TextSecondary else TrackerColors.PrimaryWhite,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        // Foreground content. Swipes only — a tap used to toggle a bunk, but the row
        // is also the thing being read, so an accidental tap could move the forecast
        // under the reader.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .clip(RoundedCornerShape(6.dp))
                // Opaque base first: the reveal behind this row must never show
                // through, whether or not this row paints a tint of its own.
                .background(TrackerColors.SurfaceDark)
                .background(rowBackground)
                .border(1.dp, rowBorder, RoundedCornerShape(6.dp))
                .then(
                    // Sundays already have nothing scheduled; every other day — today
                    // included — takes the bunk (left) and holiday (right) swipes.
                    if (!row.isSunday) {
                        Modifier
                            .pointerInput(row.dateText, row.isAbsent, row.isHoliday) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        when {
                                            dragOffset < -50f -> onSwipeBunk()
                                            dragOffset > 50f -> onSwipeHoliday()
                                        }
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        dragOffset = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset = (dragOffset + dragAmount).coerceIn(-100f, 100f)
                                    }
                                )
                            }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(rowAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date column with absent indicator, or the off-day marker
                Row(
                    modifier = Modifier.weight(1.3f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (row.isBunked) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(TrackerColors.DangerRose)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = when {
                            row.isSunday -> "${row.dateText} (Sun)"
                            row.isHoliday -> "${row.dateText} (Hol)"
                            else -> row.dateText
                        },
                        color = when {
                            row.isSunday -> TrackerColors.TextSubtle
                            row.isHoliday -> TrackerColors.TextMuted
                            row.isBunked -> TrackerColors.DangerRose
                            isToday -> TrackerColors.TextPrimary
                            else -> TrackerColors.TextSecondary
                        },
                        fontSize = if (row.isOffDay) 10.sp else 11.sp,
                        fontWeight = if (isToday || row.isBunked) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Projected percentage
                Text(
                    text = formatPercentage(row.percentage),
                    color = pctColor,
                    fontSize = if (row.isOffDay) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1.0f)
                )

                // Attended / Held count
                Text(
                    text = if (row.isOffDay) "—" else "${row.attended}/${row.held}",
                    color = when {
                        row.isSunday -> TrackerColors.TextSubtle
                        row.isHoliday -> TrackerColors.TextMuted
                        row.isBunked -> TrackerColors.DangerRose.copy(alpha = 0.8f)
                        else -> TrackerColors.TextMuted
                    },
                    fontSize = if (row.isOffDay) 10.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(1.1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Buffer Badge / Bunk Badge / Off-Day Badge
                Box(
                    modifier = Modifier.width(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        row.isOffDay -> {
                            Text(
                                text = "OFF",
                                // A holiday is the user's own mark, so it reads
                                // brighter than a Sunday nobody can change.
                                color = if (row.isHoliday) TrackerColors.TextSecondary
                                else TrackerColors.TextSubtle,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        row.isAbsent -> {
                            Text(
                                text = "BUNK",
                                color = TrackerColors.DangerRose,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        row.isPartiallyBunked -> {
                            Text(
                                text = "-${row.bunkedPeriods}",
                                color = TrackerColors.DangerRose,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        else -> {
                            SkippableBadge(count = row.skippable)
                        }
                    }
                }
            }
        }
    }
}

/** Minimal buffer text: periods that can still be skipped from this day on. */
@Composable
private fun SkippableBadge(count: Int) {
    Text(
        text = if (count > 0) "+$count" else "0",
        color = if (count > 0) TrackerColors.SafeEmerald else TrackerColors.TextSubtle,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center
    )
}
