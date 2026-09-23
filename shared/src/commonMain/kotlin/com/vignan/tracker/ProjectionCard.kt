package com.vignan.tracker

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Attendance as per data" projection: for each of the next N days, what the
 * overall attendance WILL be at the end of that day if every scheduled class
 * is attended, plus the bunk buffer — how many periods could be skipped from
 * that point on and still land exactly on the target:
 *
 *     attended / (held + skips) >= targetPct   →   skips = (4·attended − 3·held) / 3
 *
 * (Skipped classes still count as conducted, hence held grows by skips.)
 * Future class counts come from the college's repeating weekly timetable.
 */
data class ProjectionRow(
    val dateMs: Long,
    val dateText: String,      // display-ready; formatted once per projection build
    val classesThatDay: Int,   // classes added to the tally by this day
    val attended: Int,         // cumulative at the END of this day
    val held: Int,
    val percentage: Double,
    val skippable: Int         // periods that can still be skipped
)

/** (4·a − 3·h) / 3, floored, never negative — buffer to stay exactly at 75%. */
private fun skippablePeriods(attended: Int, held: Int): Int =
    ((4 * attended - 3 * held) / 3).coerceAtLeast(0)

/** Calendar.DAY_OF_WEEK (1=Sun..7=Sat) → weekly-map index (0=Sun..6=Sat). */
private fun dayMapIndex(cal: java.util.Calendar): Int = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1

/** Classes for a weekday from the weekly timetable; Sunday/holiday → 0. */
private fun scheduledClasses(weekly: Map<Int, List<TTItem>>, mapIdx: Int): Int =
    weekly[mapIdx].orEmpty().sumOf { it.slots.size }

/** "2026-09-22T20:07:57" → minutes-of-day (1207), or null when unusable. */
private fun parseScrapedMinute(scrapedAt: String?): Int? {
    if (scrapedAt == null || scrapedAt.length < 16 || scrapedAt.getOrNull(10) != 'T') return null
    val h = scrapedAt.substring(11, 13).toIntOrNull() ?: return null
    val m = scrapedAt.substring(14, 16).toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/**
 * Today's row counts only classes still ahead at scrape time: everything with
 * endMin already past is assumed attended and is already inside the snapshot.
 */
private fun classesRemainingToday(items: List<TTItem>, nowMinute: Int?): Int {
    val total = items.sumOf { it.slots.size }
    if (nowMinute == null) return total
    val elapsed = items.sumOf { if (it.endMin <= nowMinute) it.slots.size else 0 }
    return (total - elapsed).coerceIn(0, total)
}

fun buildAttendanceProjection(live: LiveAttendanceResponse, days: Int): List<ProjectionRow> {
    val agg = live.profile.aggregate
    if (agg.held <= 0) return emptyList()

    val weekly = buildWeeklyTimetable(live.attendance.timetable, live.attendance.faculty)
    val scrapeMinute = parseScrapedMinute(live.scrapedAt)
    val cal = java.util.Calendar.getInstance()
    // One formatter reused for every row — SimpleDateFormat construction is
    // expensive and must never happen per row / per recomposition.
    val dateFormat = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US)

    var attended = agg.attended
    var held = agg.held
    val rows = mutableListOf<ProjectionRow>()

    // Row 0 — the snapshot itself ("present attendance").
    rows += ProjectionRow(
        dateMs = cal.timeInMillis,
        dateText = dateFormat.format(java.util.Date(cal.timeInMillis)),
        classesThatDay = classesRemainingToday(weekly[dayMapIndex(cal)].orEmpty(), scrapeMinute),
        attended = attended,
        held = held,
        percentage = attended * 100.0 / held,
        skippable = skippablePeriods(attended, held)
    )

    // Rows 1..N−1 — end of each future day, full attendance assumed.
    for (offset in 1 until days) {
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val classes = scheduledClasses(weekly, dayMapIndex(cal))
        attended += classes
        held += classes
        rows += ProjectionRow(
            dateMs = cal.timeInMillis,
            dateText = dateFormat.format(java.util.Date(cal.timeInMillis)),
            classesThatDay = classes,
            attended = attended,
            held = held,
            percentage = attended * 100.0 / held,
            skippable = skippablePeriods(attended, held)
        )
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
    val rows = remember(liveResponse, range) {
        liveResponse?.let { buildAttendanceProjection(it, range) } ?: emptyList()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            // Header: Title & Subtitle + 30D/60D toggle
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
                        text = "Daily forecast if all scheduled classes are attended",
                        color = TrackerColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PROJECTION_RANGES.forEach { r ->
                        RangeChip(label = "${r}D", selected = range == r, onClick = { range = r })
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
                            ProjectionLine(row = row, isToday = index == 0)
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
private fun ProjectionLine(row: ProjectionRow, isToday: Boolean) {
    val pctColor = when {
        row.percentage >= 80.0 -> TrackerColors.SafeEmerald
        row.percentage >= 75.0 -> TrackerColors.TextPrimary
        else -> TrackerColors.DangerRose
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isToday) TrackerColors.SurfaceElevated else TrackerColors.SurfaceDark)
            .border(
                1.dp,
                if (isToday) TrackerColors.HairlineBorderLight else TrackerColors.HairlineBorder.copy(alpha = 0.6f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.dateText,
                color = if (isToday) TrackerColors.TextPrimary else TrackerColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.3f)
            )
            Text(
                text = formatPercentage(row.percentage),
                color = pctColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1.0f)
            )
            Text(
                text = "${row.attended}/${row.held}",
                color = TrackerColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(1.1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.width(44.dp),
                contentAlignment = Alignment.Center
            ) {
                SkippableBadge(count = row.skippable)
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
            color = if (count > 0) TrackerColors.SafeEmerald else TrackerColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
