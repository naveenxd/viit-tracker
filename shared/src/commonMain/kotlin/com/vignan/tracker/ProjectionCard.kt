package com.vignan.tracker

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * "Attendance as per data" projection: for each of the next N days, what the
 * overall attendance WILL be at the end of that day if scheduled classes are attended,
 * plus the skippable buffer to stay above 75%.
 *
 * Sundays are present but greyed out. Supports simulated absences cascading forward.
 */
data class ProjectionRow(
    val dateMs: Long,
    val dateText: String,      // display-ready; formatted once per projection build
    val isSunday: Boolean = false, // true for Sundays (greyed out)
    val classesThatDay: Int,   // classes added to the tally by this day
    val attended: Int,         // cumulative at the END of this day
    val held: Int,
    val percentage: Double,
    val skippable: Int,        // periods that can still be skipped
    val isAbsent: Boolean = false // user marked absent in simulation
)

/** (4·a − 3·h) / 3, floored, never negative — buffer to stay exactly at 75%. */
private fun skippablePeriods(attended: Int, held: Int): Int =
    ((4 * attended - 3 * held) / 3).coerceAtLeast(0)

/** Calendar.DAY_OF_WEEK (1=Sun..7=Sat) → weekly-map index (0=Sun..6=Sat). */
private fun dayMapIndex(cal: java.util.Calendar): Int = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1

/** Classes for a weekday from the weekly timetable; Sunday/holiday → 0. */
private fun scheduledClasses(weekly: Map<Int, List<TTItem>>, mapIdx: Int): Int =
    weekly[mapIdx].orEmpty().sumOf { it.slots.size }

/**
 * Builds the attendance projection list for the given number of days.
 * - Row 0 represents the baseline: current end-of-day attendance snapshot directly from data.
 * - Future days (starting tomorrow) project attendance assuming scheduled classes are attended,
 *   or missed if included in [absentDates].
 * - Sundays are present in sequence but marked [isSunday = true] (0 classes, greyed out).
 */
fun buildAttendanceProjection(
    live: LiveAttendanceResponse,
    days: Int,
    absentDates: Set<String> = emptySet()
): List<ProjectionRow> {
    val agg = live.profile.aggregate
    if (agg.held <= 0) return emptyList()

    val weekly = buildWeeklyTimetable(live.attendance.timetable, live.attendance.faculty)
    val cal = java.util.Calendar.getInstance()
    // "d MMM" (e.g. "23 Sep") keeps dates compact and guarantees room for labels without clipping
    val dateFormat = java.text.SimpleDateFormat("d MMM", java.util.Locale.US)

    var attended = agg.attended
    var held = agg.held
    val rows = mutableListOf<ProjectionRow>()

    // Row 0 — Today's baseline: represents current standing from scraped data (no added classes)
    val isTodaySunday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
    val todayDateText = dateFormat.format(java.util.Date(cal.timeInMillis))

    rows += ProjectionRow(
        dateMs = cal.timeInMillis,
        dateText = todayDateText,
        isSunday = isTodaySunday,
        classesThatDay = 0,
        attended = attended,
        held = held,
        percentage = if (held > 0) attended * 100.0 / held else 0.0,
        skippable = skippablePeriods(attended, held),
        isAbsent = false
    )

    // Future days: iterate starting tomorrow until we reach requested count of days
    while (rows.size < days) {
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)

        val isSunday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
        val dateText = dateFormat.format(java.util.Date(cal.timeInMillis))

        if (isSunday) {
            // Sunday: greyed out, 0 classes scheduled, stats carry forward from Saturday
            rows += ProjectionRow(
                dateMs = cal.timeInMillis,
                dateText = dateText,
                isSunday = true,
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

private val PROJECTION_RANGES = listOf(30, 60)

@Composable
fun ProjectionCard(
    liveResponse: LiveAttendanceResponse?,
    hasData: Boolean,
    modifier: Modifier = Modifier
) {
    var range by remember { mutableStateOf(30) }
    var absentDates by remember { mutableStateOf(setOf<String>()) }

    val rows = remember(liveResponse, range, absentDates) {
        liveResponse?.let { buildAttendanceProjection(it, range, absentDates) } ?: emptyList()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            // Header: Title & Subtitle + Bunk Reset / Range Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ATTENDANCE PROJECTION",
                        color = TrackerColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (absentDates.isNotEmpty()) {
                            "Simulating ${absentDates.size} day(s) absent • Tap or swipe to toggle"
                        } else {
                            "Daily forecast • Tap or swipe to simulate absence"
                        },
                        color = if (absentDates.isNotEmpty()) TrackerColors.DangerRose else TrackerColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (absentDates.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(TrackerColors.DangerRoseSubtle)
                                .border(1.dp, TrackerColors.DangerRose.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                                .clickable { absentDates = emptySet() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "RESET",
                                color = TrackerColors.DangerRose,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PROJECTION_RANGES.forEach { r ->
                            RangeChip(label = "${r}D", selected = range == r, onClick = { range = r })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TrackerColors.HairlineBorder)
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                                onToggleAbsent = {
                                    if (!row.isSunday) {
                                        absentDates = if (row.dateText in absentDates) {
                                            absentDates - row.dateText
                                        } else {
                                            absentDates + row.dateText
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) TrackerColors.PrimaryWhite else TrackerColors.SurfaceInput)
            .border(
                1.dp,
                if (selected) TrackerColors.PrimaryWhite else TrackerColors.HairlineBorder,
                RoundedCornerShape(5.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = if (selected) TrackerColors.PureBlack else TrackerColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ProjectionLine(
    row: ProjectionRow,
    isToday: Boolean,
    onToggleAbsent: () -> Unit
) {
    var dragOffset by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dragOffset"
    )

    // Solid 100% opaque backgrounds prevent ANY background bleed through
    val rowBackground = when {
        row.isSunday -> TrackerColors.SurfaceDark
        row.isAbsent -> Color(0xFF220D13) // Solid dark rose
        isToday -> TrackerColors.SurfaceElevated
        else -> TrackerColors.SurfaceDark
    }

    val rowBorder = when {
        row.isSunday -> TrackerColors.HairlineBorder.copy(alpha = 0.25f)
        row.isAbsent -> TrackerColors.DangerRose.copy(alpha = 0.6f)
        isToday -> TrackerColors.HairlineBorderLight
        else -> TrackerColors.HairlineBorder.copy(alpha = 0.6f)
    }

    // High contrast threshold: always green for >= 75%
    val pctColor = when {
        row.isSunday -> TrackerColors.TextSubtle
        row.isAbsent -> TrackerColors.DangerRose
        row.percentage >= 75.0 -> TrackerColors.SafeEmerald // Always green for >= 75%, never turns white!
        row.percentage >= 70.0 -> TrackerColors.WarningAmber
        else -> TrackerColors.DangerRose
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(if (row.isSunday) Modifier.alpha(0.35f) else Modifier)
    ) {
        // Background reveal ONLY when swiping actively on future days (dragOffset < -8f)
        // If absent, shows neutral "RESTORE" to prevent ANY green-on-red overlap!
        if (!row.isSunday && !isToday && dragOffset < -8f) {
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

        // Foreground content with swipe gesture & tap to toggle (future days only)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .clip(RoundedCornerShape(6.dp))
                .background(rowBackground)
                .border(1.dp, rowBorder, RoundedCornerShape(6.dp))
                .then(
                    if (!row.isSunday && !isToday) {
                        Modifier
                            .pointerInput(row.dateText, row.isAbsent) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (dragOffset < -50f) {
                                            onToggleAbsent()
                                        }
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        dragOffset = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset = (dragOffset + dragAmount).coerceIn(-100f, 0f)
                                    }
                                )
                            }
                            .clickable { onToggleAbsent() }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date column with absent indicator or Sunday label
                Row(
                    modifier = Modifier.weight(1.3f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (row.isAbsent) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(TrackerColors.DangerRose)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = if (row.isSunday) "${row.dateText} (Sun)" else row.dateText,
                        color = when {
                            row.isSunday -> TrackerColors.TextSubtle
                            row.isAbsent -> TrackerColors.DangerRose
                            isToday -> TrackerColors.TextPrimary
                            else -> TrackerColors.TextSecondary
                        },
                        fontSize = if (row.isSunday) 10.sp else 11.sp,
                        fontWeight = if (isToday || row.isAbsent) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Projected percentage
                Text(
                    text = formatPercentage(row.percentage),
                    color = pctColor,
                    fontSize = if (row.isSunday) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1.0f)
                )

                // Attended / Held count
                Text(
                    text = if (row.isSunday) "—" else "${row.attended}/${row.held}",
                    color = when {
                        row.isSunday -> TrackerColors.TextSubtle
                        row.isAbsent -> TrackerColors.DangerRose.copy(alpha = 0.8f)
                        else -> TrackerColors.TextMuted
                    },
                    fontSize = if (row.isSunday) 10.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(1.1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Buffer Badge / Bunk Badge / Sunday Off
                Box(
                    modifier = Modifier.width(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        row.isSunday -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TrackerColors.SurfaceInput)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "OFF",
                                    color = TrackerColors.TextSubtle,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        row.isAbsent -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF3B151E))
                                    .border(1.dp, TrackerColors.DangerRose, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "BUNK",
                                    color = TrackerColors.DangerRose,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
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

/** Refined pill: periods that can still be skipped from this day on. */
@Composable
private fun SkippableBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (count > 0) TrackerColors.SafeEmeraldSubtle else TrackerColors.SurfaceInput)
            .border(
                1.dp,
                if (count > 0) TrackerColors.SafeEmerald.copy(alpha = 0.35f) else TrackerColors.HairlineBorder,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (count > 0) "+$count" else "0",
            color = if (count > 0) TrackerColors.SafeEmerald else TrackerColors.TextSubtle,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
