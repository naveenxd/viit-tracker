package com.vignan.tracker

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlin.math.abs

// ===================== Timetable data (built from the backend) =====================

/**
 * College-wide period grid: slot N maps to fixed clock times.
 * This is timetable *structure*, not user-specific data — everything else
 * (subjects, days, faculty, section) comes from the backend.
 */
data class SlotInfo(val n: Int, val label: String, val startMin: Int, val endMin: Int)

/** One rendered timetable row; [slots] may span multiple consecutive periods. */
data class TTItem(
    val slots: List<Int>,
    val name: String,
    val color: Color,
    val tag: String? = null,
    val startMin: Int,
    val endMin: Int,
    val timeLabel: String,
    val facultyName: String? = null,
)

val SLOTS = listOf(
    SlotInfo(1, "8.45–9.35", 8 * 60 + 45, 9 * 60 + 35),
    SlotInfo(2, "9.35–10.25", 9 * 60 + 35, 10 * 60 + 25),
    SlotInfo(3, "10.50–11.40", 10 * 60 + 50, 11 * 60 + 40),
    SlotInfo(4, "11.40–12.30", 11 * 60 + 40, 12 * 60 + 30),
    SlotInfo(5, "12.30–1.30", 12 * 60 + 30, 13 * 60 + 30),
    SlotInfo(6, "2.20–3.10", 14 * 60 + 20, 15 * 60 + 10),
    SlotInfo(7, "3.10–4.00", 15 * 60 + 10, 16 * 60)
)

/** Two-letter compact labels for the tighter day selector pills. */
private val DAY_LABELS = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

/** Fixed slot geometry — every day renders the exact same vertical rhythm. */
private val SLOT_ROW_H = 56.dp
private val SLOT_GAP = 6.dp

private val TimetableMuted = Color(0xFF2C3548)
private val BreakColor = Color(0xFF5B6579)

private val SUBJECT_PALETTE = listOf(
    Color(0xFF3B7DD8), Color(0xFF4FAE6B), Color(0xFFE79A68), Color(0xFF8FB4E0),
    Color(0xFFC99A2E), Color(0xFFC2643A), Color(0xFF8C5FC4), Color(0xFF2FA3A0),
    Color(0xFFD95F8C), Color(0xFFA9CF9B)
)

private val LAB_RX = Regex("""\blab\b""", RegexOption.IGNORE_CASE)
private val TEST_RX = Regex("""\btest\b""", RegexOption.IGNORE_CASE)
private val CERT_RX = Regex("""\bcertif""", RegexOption.IGNORE_CASE)
private val TIME_RX = Regex("""(\d{1,2})[.:](\d{2})""")

private fun dayIndexOf(name: String): Int? = when (name.trim().uppercase().take(3)) {
    "MON" -> 1
    "TUE" -> 2
    "WED" -> 3
    "THU" -> 4
    "FRI" -> 5
    "SAT" -> 6
    "SUN" -> 0
    else -> null
}

/** Strips a leading course code like "22CSB3103-OOPS" down to the readable name. */
private fun prettySubject(raw: String): String {
    val s = raw.trim()
    if (s.contains('-')) {
        val parts = s.split("-", limit = 2)
        if (parts[0].any { it.isDigit() }) return parts[1].trim()
    }
    return s
}

private fun detectTag(subject: String): String? = when {
    LAB_RX.containsMatchIn(subject) -> "LAB"
    TEST_RX.containsMatchIn(subject) -> "TEST"
    CERT_RX.containsMatchIn(subject) -> "CERT"
    else -> null
}

/** Distinct color per subject; stable across days via a hash of the subject name. */
private fun resolveColor(name: String): Color = when (detectTag(name)) {
    "LAB" -> Color(0xFF8C5FC4)
    "TEST" -> Color(0xFFE0574F)
    "CERT" -> Color(0xFFA9CF9B)
    else -> SUBJECT_PALETTE[abs(name.trim().uppercase().hashCode()) % SUBJECT_PALETTE.size]
}

/** Parses "8.45-9.35" / "08:45 - 09:35" style published times into minutes-of-day. */
private fun parseTimeRange(time: String?): Pair<Int, Int>? {
    if (time.isNullOrBlank()) return null
    val matches = TIME_RX.findAll(time).toList()
    if (matches.size < 2) return null
    val (sh, sm) = matches.first().destructured
    val (eh, em) = matches.last().destructured
    val startMin = sh.toIntOrNull()?.times(60)?.plus(sm.toIntOrNull() ?: return null) ?: return null
    val endMin = eh.toIntOrNull()?.times(60)?.plus(em.toIntOrNull() ?: return null) ?: return null
    return if (endMin > startMin) startMin to endMin else null
}

/** 12-hour clock without meridiem, matching the college's "12.30–1.30" convention. */
private fun formatClock(min: Int): String {
    val h24 = (min / 60) % 24
    val m = min % 60
    val h = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return "$h.${m.toString().padStart(2, '0')}"
}

/** Finds the faculty allocation for a subject: code containment first, then word overlap. */
private fun resolveFaculty(subject: String, faculty: List<FacultyAllocation>): String? {
    if (faculty.isEmpty()) return null
    val target = prettySubject(subject).trim().uppercase()
    if (target.isEmpty()) return null

    faculty.firstOrNull { f ->
        val code = f.code.trim().uppercase()
        code.length >= 3 && (target.contains(code) || code.contains(target))
    }?.let { return it.faculty.trim().ifBlank { null } }

    val targetWords = target.split(Regex("\\W+")).filter { it.length >= 4 }.toSet()
    if (targetWords.isEmpty()) return null
    return faculty.firstOrNull { f ->
        f.subject.trim().uppercase()
            .split(Regex("\\W+"))
            .any { it.length >= 4 && it in targetWords }
    }?.faculty?.trim()?.ifBlank { null }
}

private fun buildItem(run: List<TimetablePeriod>, faculty: List<FacultyAllocation>): TTItem? {
    val first = run.first()
    val name = prettySubject(first.subject).ifBlank { "Class" }
    val slotStart = SLOTS.firstOrNull { it.n == first.slotNumber } ?: return null
    val slotEnd = SLOTS.firstOrNull { it.n == run.last().slotNumber } ?: slotStart

    // The published `time` string covers a single period; multi-period runs use the slot grid.
    val parsed = parseTimeRange(first.time).takeIf { run.size == 1 }
    val startMin = parsed?.first ?: slotStart.startMin
    val endMin = parsed?.second ?: slotEnd.endMin

    return TTItem(
        slots = run.map { it.slotNumber },
        name = name,
        color = resolveColor(name),
        tag = detectTag(name),
        startMin = startMin,
        endMin = endMin,
        timeLabel = "${formatClock(startMin)}–${formatClock(endMin)}",
        facultyName = resolveFaculty(first.subject, faculty)
    )
}

/** Maps the backend's weekly timetable into renderable rows keyed by weekday index (0=Sun..6=Sat). */
fun buildWeeklyTimetable(
    timetable: List<TimetableDay>,
    faculty: List<FacultyAllocation>
): Map<Int, List<TTItem>> {
    if (timetable.isEmpty()) return emptyMap()
    val built = mutableMapOf<Int, List<TTItem>>()
    for (day in timetable) {
        val dayIdx = dayIndexOf(day.day) ?: continue
        val periods = day.periods
            .filter { it.slotNumber in 1..SLOTS.size }
            .sortedBy { it.slotNumber }
        val items = mutableListOf<TTItem>()
        var i = 0
        while (i < periods.size) {
            var j = i
            while (j + 1 < periods.size &&
                periods[j + 1].slotNumber == periods[j].slotNumber + 1 &&
                periods[j + 1].subject.trim().equals(periods[j].subject.trim(), ignoreCase = true)
            ) j++
            buildItem(periods.subList(i, j + 1), faculty)?.let { items += it }
            i = j + 1
        }
        built[dayIdx] = items
    }
    return built
}

// ===================== Screen =====================

@Composable
fun TimetableScreen(
    liveResponse: LiveAttendanceResponse? = null
) {
    val attendance = liveResponse?.attendance
    val facultyList = remember(attendance) { attendance?.faculty.orEmpty() }
    val weekly = remember(attendance?.timetable, facultyList) {
        buildWeeklyTimetable(attendance?.timetable.orEmpty(), facultyList)
    }

    // Live clock — keeps the NOW card and the current-period highlight fresh.
    val timeInfo by produceState(initialValue = getCurrentTimeInfo()) {
        while (true) {
            value = getCurrentTimeInfo()
            delay(30_000)
        }
    }
    val todayIdx = timeInfo.dayOfWeekIdx
    var selectedDay by remember { mutableStateOf(todayIdx) }
    var showFacultyDialog by remember { mutableStateOf(false) }

    val profile = liveResponse?.profile
    val semesterLabel = profile?.semester?.trim()?.takeIf { it.isNotEmpty() }
    val branchLabel = profile?.branch?.trim()?.takeIf { it.isNotEmpty() }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section header — straight from the student's profile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (semesterLabel != null) "VIGNAN'S IIT  •  ${semesterLabel.uppercase()}" else "VIGNAN'S IIT",
                    color = TrackerColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = branchLabel ?: "TIMETABLE",
                    color = TrackerColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (facultyList.isNotEmpty()) {
                Spacer(modifier = Modifier.width(10.dp))
                FacultyChip(count = facultyList.size) { showFacultyDialog = true }
            }
        }

        if (weekly.isEmpty()) {
            TimetableUnavailableCard()
        } else {
            RealTimeNowCard(timeInfo = timeInfo, weekly = weekly)

            Spacer(modifier = Modifier.height(16.dp))

            DaySelectorTabs(
                selectedDay = selectedDay,
                todayIdx = todayIdx,
                onSelect = { selectedDay = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            val dayItems = weekly[selectedDay].orEmpty()
            if (dayItems.isEmpty()) {
                NoClassesCard(isSunday = selectedDay == 0)
            } else {
                SlotGrid(
                    dayItems = dayItems,
                    isToday = selectedDay == todayIdx,
                    nowMin = timeInfo.minutesOfDay
                )
            }
        }

    }

    if (showFacultyDialog) {
        FacultyDialog(facultyList = facultyList, onDismiss = { showFacultyDialog = false })
    }
}

// ===================== Fixed-slot grid =====================

/**
 * Renders all 7 college slots at fixed heights — merged (multi-period) classes
 * simply span their slots, free slots render as ghost rows. Total column height
 * is identical on every day, so switching days never shifts content vertically.
 */
@Composable
private fun SlotGrid(
    dayItems: List<TTItem>,
    isToday: Boolean,
    nowMin: Int
) {
    val processed = mutableSetOf<Int>()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SLOT_GAP)
    ) {
        SLOTS.forEach { slot ->
            if (slot.n in processed) return@forEach

            val item = dayItems.find { it.slots.first() == slot.n }
            if (item != null) {
                processed += item.slots
                val span = item.slots.size
                PeriodRowCard(
                    timeLabel = item.timeLabel,
                    name = item.name,
                    color = item.color,
                    tag = item.tag,
                    facultyName = item.facultyName,
                    isCurrent = isToday && nowMin >= item.startMin && nowMin < item.endMin,
                    height = SLOT_ROW_H * span + SLOT_GAP * (span - 1)
                )
            } else {
                processed += slot.n
                if (slot.n == 5) {
                    GhostRowCard(
                        timeLabel = slot.label,
                        title = "Lunch Break",
                        tag = "BREAK",
                        tagColor = BreakColor
                    )
                } else {
                    GhostRowCard(timeLabel = slot.label, title = "Free")
                }
            }
        }
    }
}

@Composable
private fun PeriodRowCard(
    timeLabel: String,
    name: String,
    color: Color,
    tag: String?,
    facultyName: String?,
    isCurrent: Boolean,
    height: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) TrackerColors.SurfaceElevated else TrackerColors.SurfaceDark)
            .border(
                width = 1.dp,
                color = if (isCurrent) TrackerColors.WarningAmber else TrackerColors.HairlineBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height * 0.5f)
                    .clip(CircleShape)
                    .background(color)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Fixed time gutter — aligns across every day
            Row(
                modifier = Modifier.width(80.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCurrent) {
                    PulseDot(color = TrackerColors.WarningAmber)
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = timeLabel,
                    color = if (isCurrent) TrackerColors.TextSecondary else TrackerColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = TrackerColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (facultyName != null) {
                    Text(
                        text = facultyName,
                        color = TrackerColors.TextMuted,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (tag != null) {
                TagChip(text = tag, color = color)
            }
        }
    }
}

/** Flat, quiet row for free periods / lunch — clearly "empty" next to class rows. */
@Composable
private fun GhostRowCard(
    timeLabel: String,
    title: String,
    tag: String? = null,
    tagColor: Color? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SLOT_ROW_H)
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.PureBlack)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeLabel,
                color = TrackerColors.TextSubtle,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(80.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = if (tag != null) TrackerColors.TextMuted else TrackerColors.TextSubtle,
                fontSize = 13.sp,
                fontWeight = if (tag != null) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (tag != null) FontFamily.SansSerif else FontFamily.Serif,
                modifier = Modifier.weight(1f)
            )
            if (tag != null && tagColor != null) {
                TagChip(text = tag, color = tagColor)
            }
        }
    }
}

@Composable
private fun NoClassesCard(isSunday: Boolean) {
    // Match the slot grid's exact height so switching days never shifts content.
    val gridHeight = SLOT_ROW_H * SLOTS.size + SLOT_GAP * (SLOTS.size - 1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NO CLASSES",
                color = TrackerColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSunday) "It's Sunday — enjoy the day off." else "Nothing scheduled for this day.",
                color = TrackerColors.TextSubtle,
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PulseDot(color: Color) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun TagChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

// ===================== NOW card =====================

@Composable
private fun RealTimeNowCard(
    timeInfo: TimeInfo,
    weekly: Map<Int, List<TTItem>>
) {
    val nowMin = timeInfo.minutesOfDay
    val items = weekly[timeInfo.dayOfWeekIdx].orEmpty()

    val breakTime = 10 * 60 + 25..10 * 60 + 50
    val lunchTime = 12 * 60 + 30..14 * 60 + 20

    var statusLabel = "TODAY"
    var statusTitle = "No classes"
    var statusTime = "Off"
    var statusColor = TimetableMuted
    var facultyInfo: String? = null

    if (items.isNotEmpty()) {
        when {
            nowMin in breakTime -> {
                statusLabel = "BREAK"
                statusTitle = "Short break"
                statusTime = "10.25–10.50"
                statusColor = BreakColor
            }
            nowMin in lunchTime -> {
                statusLabel = "LUNCH"
                statusTitle = "Lunch break"
                statusTime = "12.30–2.20"
                statusColor = BreakColor
            }
            else -> {
                val current = items.firstOrNull { nowMin >= it.startMin && nowMin < it.endMin }
                val next = if (current == null) items.firstOrNull { it.startMin > nowMin } else null

                when {
                    current != null -> {
                        statusLabel = "IN PROGRESS"
                        statusTitle = current.name
                        statusTime = "${formatClock(current.startMin)}–${formatClock(current.endMin)}"
                        statusColor = current.color
                        facultyInfo = current.facultyName
                    }
                    next != null -> {
                        statusLabel = "UP NEXT"
                        statusTitle = next.name
                        statusTime = "Starts ${formatClock(next.startMin)}"
                        statusColor = next.color
                        facultyInfo = next.facultyName
                    }
                    else -> {
                        statusLabel = "TODAY"
                        statusTitle = "Done for the day"
                        statusTime = "See you tomorrow"
                        statusColor = TimetableMuted
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(84.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(color = statusColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        color = TrackerColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = statusTitle,
                    color = TrackerColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = statusTime,
                    color = TrackerColors.TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (facultyInfo != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = facultyInfo,
                        color = TrackerColors.WarningAmber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ===================== Day selector =====================

@Composable
private fun DaySelectorTabs(
    selectedDay: Int,
    todayIdx: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            (0..6).forEach { dayIdx ->
                val isSelected = dayIdx == selectedDay
                val isToday = dayIdx == todayIdx

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                isSelected -> TrackerColors.PrimaryWhite
                                isToday -> TrackerColors.SurfaceElevated
                                else -> TrackerColors.SurfaceDark
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                isSelected -> TrackerColors.PrimaryWhite
                                isToday -> TrackerColors.HairlineBorderLight
                                else -> TrackerColors.HairlineBorder
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelect(dayIdx) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = DAY_LABELS[dayIdx],
                        color = if (isSelected) TrackerColors.PureBlack else TrackerColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            (0..6).forEach { dayIdx ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    dayIdx == todayIdx -> TrackerColors.WarningAmber
                                    dayIdx == selectedDay -> TrackerColors.TextSubtle
                                    else -> Color.Transparent
                                }
                            )
                    )
                }
            }
        }
    }
}

// ===================== Faculty dialog =====================

/** Compact faculty trigger that sits beside the day selector, away from the nav bar. */
@Composable
private fun FacultyChip(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "FACULTY",
                color = TrackerColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TrackerColors.SurfaceElevated)
                    .border(1.dp, TrackerColors.HairlineBorderLight, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "$count",
                    color = TrackerColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun FacultyDialog(
    facultyList: List<FacultyAllocation>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = TrackerColors.SurfaceDark,
            border = BorderStroke(1.dp, TrackerColors.HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FACULTY & COURSES",
                            color = TrackerColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "${facultyList.size} allocations for your section",
                            color = TrackerColors.TextSubtle,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(TrackerColors.SurfaceElevated)
                            .border(1.dp, TrackerColors.HairlineBorder, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = TrackerColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(facultyList) { fac ->
                        FacultyDialogRow(faculty = fac)
                    }
                }
            }
        }
    }
}

@Composable
private fun FacultyDialogRow(faculty: FacultyAllocation) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TrackerColors.SurfaceElevated)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(resolveColor(faculty.subject))
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = faculty.code.ifBlank { faculty.subject },
                color = TrackerColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = faculty.subject,
                color = TrackerColors.TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = faculty.faculty,
            color = TrackerColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp)
        )
    }
}

// ===================== Empty state =====================

@Composable
private fun TimetableUnavailableCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TIMETABLE UNAVAILABLE",
                color = TrackerColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap SYNC in the top bar to pull the latest schedule for your section.",
                color = TrackerColors.TextSubtle,
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }
    }
}
