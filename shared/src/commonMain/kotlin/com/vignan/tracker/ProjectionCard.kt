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
import androidx.compose.foundation.lazy.LazyColumn
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

    var attended = agg.attended
    var held = agg.held
    val rows = mutableListOf<ProjectionRow>()

    // Row 0 — the snapshot itself ("present attendance").
    rows += ProjectionRow(
        dateMs = cal.timeInMillis,
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

/** Max rows visible before the list scrolls internally (keeps the card compact). */
private const val VISIBLE_ROWS = 12

@Composable
fun ProjectionCard(
    liveResponse: LiveAttendanceResponse?,
    hasData: Boolean,
    modifier: Modifier = Modifier
) {
    // 30/60 toggle is in-memory for now; hoist to settings when one exists.
    var range by remember { mutableStateOf(30) }
    val rows = remember(liveResponse, range) {
        liveResponse?.let { buildAttendanceProjection(it, range) } ?: emptyList()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            // Header: title + legend on the left, 30/60 toggle on the right
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Attendance projection",
                        color = TrackerColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "End of each day, if you attend everything. N = periods you can still skip.",
                        color = TrackerColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PROJECTION_RANGES.forEach { r ->
                        RangeChip(label = "${r}D", selected = range == r, onClick = { range = r })
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TrackerColors.HairlineBorder)
            )
            Spacer(modifier = Modifier.height(10.dp))

            when {
                !hasData -> Text(
                    text = "Fetch attendance to project the next $range days.",
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                rows.isEmpty() -> Text(
                    text = "Not enough data to project yet.",
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                else -> {
                    val visible = minOf(rows.size, VISIBLE_ROWS)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((visible * 31 - 3).dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(rows.size) { i -> ProjectionLine(row = rows[i]) }
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
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) TrackerColors.SurfaceElevated else TrackerColors.SurfaceDark)
            .border(
                1.dp,
                if (selected) TrackerColors.HairlineBorderLight else TrackerColors.HairlineBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (selected) TrackerColors.PrimaryWhite else TrackerColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ProjectionLine(row: ProjectionRow) {
    val dateText = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US)
        .format(java.util.Date(row.dateMs))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(TrackerColors.SurfaceCard)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateText,
                color = TrackerColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.25f)
            )
            Text(
                text = formatPercentage(row.percentage),
                color = TrackerColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${row.attended} / ${row.held}",
                color = TrackerColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(7.dp))
            SkippableBadge(count = row.skippable)
        }
    }
}

/** Emerald pill: periods that can still be skipped from this day on. */
@Composable
private fun SkippableBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (count > 0) TrackerColors.SafeEmerald else TrackerColors.SurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$count",
            color = if (count > 0) TrackerColors.PureBlack else TrackerColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}
