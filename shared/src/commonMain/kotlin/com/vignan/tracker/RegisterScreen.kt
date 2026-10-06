package com.vignan.tracker

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** How the register matrix is presented. */
private enum class RegisterView(val label: String) {
    PARSED("PARSED"),
    RAW("RAW")
}

/**
 * Last successful register payload, kept for the process lifetime.
 *
 * The screen is composed only while its tab is selected, so leaving the tab
 * throws the local state away and a return trip would otherwise start from a
 * blank screen + full network fetch. Serving this cache first makes the revisit
 * instant; the fetch then refreshes it in the background.
 */
private object RegisterCache {
    var cached: AttendanceRegisterResponse? = null
}

/**
 * Academic register screen — the full day-by-day attendance matrix.
 *
 * PARSED: one card per recorded day, listing every subject with its P/A token.
 * RAW: the untouched matrix — subjects down the rows, dates across the columns.
 *
 * Owns its own lazy scroll containers (rather than living inside the dashboard's
 * LazyColumn) so both views compose only what is on screen and switching never
 * spikes on a full-matrix rebuild.
 */
@Composable
fun RegisterScreen(modifier: Modifier = Modifier) {
    val repository = remember { AttendanceRepository() }
    val scope = rememberCoroutineScope()

    var view by remember { mutableStateOf(RegisterView.PARSED) }
    // Seed from the cache so a revisit paints instantly instead of flashing the
    // skeleton while the network round-trip runs.
    var register by remember { mutableStateOf(RegisterCache.cached) }
    var isLoading by remember { mutableStateOf(RegisterCache.cached == null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        isLoading = true
        error = null
        scope.launch {
            repository.fetchRegister().fold(
                onSuccess = {
                    register = it
                    RegisterCache.cached = it
                },
                onFailure = {
                    // Keep any cached data on screen; only an empty screen shows the error.
                    if (register == null) {
                        error = it.message?.ifBlank { null } ?: "Could not load the register."
                    }
                }
            )
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RegisterHeader(
                latest = register?.latestDate.orEmpty(),
                isLoading = isLoading,
                onReload = { load() }
            )
            RegisterViewSelector(selected = view, onSelect = { view = it })
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading && register == null -> RegisterSkeleton()
            error != null -> RegisterErrorCard(error ?: "", onRetry = { load() })
            register == null || register!!.datesTracked.isEmpty() -> RegisterEmptyCard()
            view == RegisterView.PARSED -> RegisterParsedView(register!!)
            else -> RegisterRawView(register!!)
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun RegisterHeader(latest: String, isLoading: Boolean, onReload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REGISTER",
                color = TrackerColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TrackerColors.SurfaceDark)
                    .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(6.dp))
                    .clickable(enabled = !isLoading) { onReload() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isLoading) "· · ·" else "↻ RELOAD",
                    color = if (isLoading) TrackerColors.TextMuted else TrackerColors.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Day-by-day history",
            color = TrackerColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
        if (latest.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "latest entry  $latest",
                color = TrackerColors.TextSubtle,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ---------------------------------------------------------------- view switch

/** Segmented control: one hairline container with a filled highlight for the active view. */
@Composable
private fun RegisterViewSelector(selected: RegisterView, onSelect: (RegisterView) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RegisterView.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) TrackerColors.PrimaryWhite else Color.Transparent)
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.label,
                    color = if (isSelected) TrackerColors.PureBlack else TrackerColors.TextMuted,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

// ---------------------------------------------------------------- parsed view

/** A single day's cells, ready to render as a card. */
private data class DayEntry(val subject: String, val status: String, val held: Int, val attended: Int)

/** date -> its recorded subject cells, in subject order. Blank days never appear. */
private fun registerByDate(register: AttendanceRegisterResponse): List<Pair<String, List<DayEntry>>> =
    register.subjects
        .flatMap { s -> s.log.map { it to s.subject } }
        .filter { (log, _) -> log.held > 0 }
        .groupBy({ it.first.date }, { DayEntry(it.second, it.first.status, it.first.held, it.first.attended) })
        // Newest day first: datesTracked is oldest -> newest.
        .let { byDate -> register.datesTracked.reversed().mapNotNull { d -> byDate[d]?.let { d to it } } }

@Composable
private fun RegisterParsedView(register: AttendanceRegisterResponse) {
    val days = remember(register) { registerByDate(register) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(days, key = { it.first }) { (date, entries) ->
            RegisterDayCard(date = date, entries = entries)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterDayCard(date: String, entries: List<DayEntry>) {
    val present = entries.count { !it.status.uppercase().contains('A') }
    val absent = entries.size - present

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = date,
                    color = TrackerColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = weekdayLabel(date),
                    color = TrackerColors.TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (present > 0) CountPill(text = "$present P", color = TrackerColors.SafeEmerald)
                if (absent > 0) CountPill(text = "$absent A", color = TrackerColors.DangerRose)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            entries.forEach { entry ->
                RegisterSubjectChip(subject = entry.subject, status = entry.status)
            }
        }
    }
}

@Composable
private fun CountPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.40f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** Subject short code plus its raw P/A token, tinted by whether it was skipped. */
@Composable
private fun RegisterSubjectChip(subject: String, status: String) {
    val normalized = status.trim().uppercase().replace(" ", "")
    val wasAbsent = normalized.any { it == 'A' }
    val accent = if (wasAbsent) TrackerColors.DangerRose else TrackerColors.SafeEmerald

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = registerShortName(subject),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = ": $normalized",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------- raw view

private val RAW_SUBJECT_COL_W = 108.dp
private val RAW_ROW_H = 34.dp
private val RAW_HEADER_H = 42.dp
// Floor width: enough for the "DD/MM" column header, the widest thing a narrow
// column must show. Tokens wider than this stretch the whole column.
private val RAW_MIN_CELL_W = 36.dp

/**
 * The untouched matrix: a fixed subject column on the left, dates scrolling
 * horizontally on the right. The whole right side is a LazyRow of date columns,
 * so only the visible columns are ever composed — a long semester no longer
 * builds the entire grid on switch.
 *
 * Width is per COLUMN, not global: a date whose longest token is "PPP" grows,
 * a date of bare "P"s stays at the header floor. Cells in a column must share a
 * width or the rows would misalign, so the column takes its widest token.
 */
@Composable
private fun RegisterRawView(register: AttendanceRegisterResponse) {
    val dates = register.datesTracked
    val subjects = register.subjects

    // date -> subject -> status token, for O(1) cell lookup while drawing rows.
    val cellLookup = remember(register) {
        subjects.associate { s -> s.subject to s.log.associate { it.date to it.status } }
    }

    // Per-column width from that date's widest token, floored to the header.
    // Short days stay narrow instead of every column paying the global max.
    val columnWidths = remember(register) {
        dates.associateWith { date ->
            val widest = subjects.maxOfOrNull { s ->
                cellLookup[s.subject]?.get(date)?.trim()?.length ?: 0
            } ?: 0
            val estimated = (widest * 7).dp + 12.dp
            if (estimated > RAW_MIN_CELL_W) estimated else RAW_MIN_CELL_W
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MATRIX",
                color = TrackerColors.TextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "${subjects.size} subjects  ·  ${dates.size} days  →",
                color = TrackerColors.TextSubtle,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
        ) {
            // Frozen left column — subject names, one per row.
            Column(modifier = Modifier.width(RAW_SUBJECT_COL_W)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RAW_HEADER_H)
                        .padding(start = 14.dp, end = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "SUBJECT",
                        color = TrackerColors.TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                subjects.forEach { subject ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RAW_ROW_H)
                            .padding(start = 14.dp, end = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = registerShortName(subject.subject),
                            color = TrackerColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Hairline divider between the frozen column and the scrolling grid.
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(RAW_HEADER_H + RAW_ROW_H * subjects.size)
                    .background(TrackerColors.HairlineBorder)
            )

            // Scrolling grid: one LazyRow item per date, each holding that day's
            // header plus a cell for every subject. Only visible dates compose.
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = 12.dp)
            ) {
                items(dates, key = { it }) { date ->
                    val columnWidth = columnWidths[date] ?: RAW_MIN_CELL_W
                    Column(modifier = Modifier.width(columnWidth)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(RAW_HEADER_H),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date,
                                color = TrackerColors.TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        subjects.forEach { subject ->
                            RawStatusCell(
                                status = cellLookup[subject.subject]?.get(date) ?: "-",
                                width = columnWidth
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

/** One matrix cell: a P/A token tinted by outcome; "–" for no class that day. */
@Composable
private fun RawStatusCell(status: String, width: Dp) {
    val normalized = status.trim().uppercase().replace(" ", "")
    val hasClass = normalized.any { it == 'P' || it == 'A' }
    val wasAbsent = normalized.any { it == 'A' }
    val accent = when {
        !hasClass -> TrackerColors.TextSubtle
        wasAbsent -> TrackerColors.DangerRose
        else -> TrackerColors.SafeEmerald
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(RAW_ROW_H),
        contentAlignment = Alignment.Center
    ) {
        Text(
            // softWrap off + a width sized to the longest token: a long run stays
            // on one line and is never clipped or wrapped mid-token.
            text = if (hasClass) normalized else "–",
            color = accent,
            fontSize = 10.sp,
            fontWeight = if (hasClass) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(if (hasClass) accent.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

// ---------------------------------------------------------------- states

/** Placeholder shown while the first register request is in flight. Fills the screen. */
@Composable
private fun RegisterSkeleton() {
    val transition = rememberInfiniteTransition(label = "registerSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "registerSkeletonAlpha"
    )

    // Inside a full-height lazy list, so the count just needs to exceed the
    // tallest phone screen — the list clips the tail.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(count = 8) { i ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TrackerColors.SurfaceDark)
                    .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(TrackerColors.SurfaceElevated.copy(alpha = alpha))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f - (i % 4) * 0.12f)
                        .height(22.dp)
                        .clip(CircleShape)
                        .background(TrackerColors.SurfaceElevated.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
private fun RegisterErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.DangerRose.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(TrackerColors.DangerRose)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "COULD NOT LOAD",
                color = TrackerColors.DangerRose,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = TrackerColors.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TrackerColors.PrimaryWhite)
                .clickable { onRetry() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Try again",
                color = TrackerColors.PureBlack,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
private fun RegisterEmptyCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "NO REGISTER ENTRIES",
            color = TrackerColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "The portal has not recorded any class days yet.",
            color = TrackerColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ---------------------------------------------------------------- helpers

/** "DL & CO - Something long" → "DL & CO"; the short code the portal prints first. */
private fun registerShortName(raw: String): String =
    raw.split(" - ").first().trim().uppercase().ifBlank { "SUBJECT" }

/** "05/08" → "WED"; blank when the header is not a bare DD/MM date. */
private fun weekdayLabel(date: String): String {
    val parts = date.trim().split("/")
    if (parts.size < 2) return ""
    val dd = parts[0].toIntOrNull() ?: return ""
    val mm = parts[1].toIntOrNull() ?: return ""
    if (dd !in 1..31 || mm !in 1..12) return ""
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.MONTH, mm - 1)
        set(java.util.Calendar.DAY_OF_MONTH, dd)
    }
    return when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.SUNDAY -> "SUN"
        java.util.Calendar.MONDAY -> "MON"
        java.util.Calendar.TUESDAY -> "TUE"
        java.util.Calendar.WEDNESDAY -> "WED"
        java.util.Calendar.THURSDAY -> "THU"
        java.util.Calendar.FRIDAY -> "FRI"
        else -> "SAT"
    }
}