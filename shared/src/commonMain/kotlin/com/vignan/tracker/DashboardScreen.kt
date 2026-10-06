package com.vignan.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class MainNavTab(val label: String) {
    HOME("HOME"),
    ATTENDANCE("ATTENDANCE"),
    TIMETABLE("TIMETABLE"),
    PROFILE("PROFILE")
}

@Composable
fun DashboardScreen(
    data: AttendanceResponse?,
    liveResponse: LiveAttendanceResponse? = null,
    isRefreshing: Boolean = false,
    lastFetchDurationMs: Long? = null,
    dataVersion: Int = 0,
    onFetchClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var selectedNavTab by remember { mutableStateOf(MainNavTab.HOME) }

    // The real UI is always composed; data fills in when the response arrives.
    val subjects = data?.subjects ?: emptyList()
    val totalAttended = subjects.sumOf { it.attended }
    val totalConducted = subjects.sumOf { it.conducted }
    val overallPercentage = liveResponse?.profile?.aggregate?.percentage
        ?: data?.overallPercentage
        ?: 0.0
    val hasData = data != null

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 86.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedNavTab) {
                MainNavTab.HOME -> {
                    item {
                        HeroTerminalCard(
                            studentName = data?.studentName.orEmpty(),
                            rollNumber = data?.rollNumber.orEmpty(),
                            branch = liveResponse?.profile?.branch.orEmpty(),
                            semester = liveResponse?.profile?.semester ?: "",
                            overallPercentage = overallPercentage,
                            totalAttended = totalAttended,
                            totalConducted = totalConducted,
                            hasData = hasData,
                            dataVersion = dataVersion
                        )
                    }

                    item {
                        // Badge panel defines the height (measured unbounded, so
                        // no chip row can ever clip); skips tile matches it.
                        StatRow(
                            todayContent = {
                                TodayPanel(
                                    liveResponse = liveResponse,
                                    hasData = hasData,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            skipsContent = {
                                SkipsPanel(
                                    liveResponse = liveResponse,
                                    hasData = hasData,
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }
                        )
                    }

                    item {
                        FetchAttendanceButton(
                            isLoading = isRefreshing,
                            lastFetchDurationMs = lastFetchDurationMs,
                            scrapedAt = liveResponse?.scrapedAt,
                            hasData = hasData,
                            onClick = onFetchClick
                        )
                    }

                    item {
                        ProjectionCard(
                            liveResponse = liveResponse,
                            hasData = hasData
                        )
                    }
                }

                MainNavTab.ATTENDANCE -> {
                    item {
                        ReportScreen()
                    }
                }

                MainNavTab.TIMETABLE -> {
                    item {
                        TimetableScreen(liveResponse = liveResponse)
                    }
                }

                MainNavTab.PROFILE -> {
                    item {
                        ProfileScreen(
                            liveResponse = liveResponse,
                            isRefreshing = isRefreshing,
                            onRefresh = onFetchClick,
                            onLogout = onLogout
                        )
                    }
                }
            }
        }

        // Floating pill navigation bar
        ExpressiveFloatingPillNavBar(
            selectedTab = selectedNavTab,
            onTabSelected = { selectedNavTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ---------------------------------------------------------------- hero

@Composable
private fun HeroTerminalCard(
    studentName: String,
    rollNumber: String,
    branch: String,
    semester: String,
    overallPercentage: Double,
    totalAttended: Int,
    totalConducted: Int,
    hasData: Boolean,
    dataVersion: Int = 0
) {
    val statusColor = when {
        !hasData -> TrackerColors.TextMuted
        overallPercentage >= 80.0 -> TrackerColors.SafeEmerald
        overallPercentage >= 75.0 -> TrackerColors.WarningAmber
        else -> TrackerColors.DangerRose
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        // Greeting + live status pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeGreeting(),
                color = TrackerColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            StatusPill(
                hasData = hasData,
                overallPercentage = overallPercentage,
                statusColor = statusColor
            )
        }

        // Greeting: name (roll no)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayName(studentName),
                color = TrackerColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (rollNumber.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "($rollNumber)",
                    color = TrackerColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }

        // Meta: Branch • Semester
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = listOfNotNull(
                branch.takeIf { it.isNotBlank() },
                semester.takeIf { it.isNotBlank() }
            ).joinToString("  ·  ").ifBlank { "—" },
            color = TrackerColors.TextSubtle,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Attendance block: renders the "-/-" placeholder first, then the real
        // numbers appear (fade + lift + scale) once the snapshot lands — the same
        // read as the home-screen widget's syncing → data transition. Every manual
        // refresh bumps dataVersion, so the reveal replays on each pull too.
        AnimatedContent(
            targetState = if (hasData) "data:$dataVersion" else "placeholder",
            transitionSpec = {
                (fadeIn(animationSpec = tween(420, delayMillis = 90)) +
                        scaleIn(
                            initialScale = 0.94f,
                            animationSpec = tween(420, delayMillis = 90)
                        ) +
                        slideInVertically(
                            animationSpec = tween(420, delayMillis = 90)
                        ) { it / 4 })
                    .togetherWith(fadeOut(animationSpec = tween(140)))
            },
            label = "heroAttendanceReveal"
        ) { state ->
            // "placeholder" is the "-/-" state; anything else is a real snapshot.
            val ready = state != "placeholder"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (ready) {
                        val pctText = formatPercentage(overallPercentage)
                        val intPart = pctText.substringBefore('.')
                        val fracPart = if ('.' in pctText) "." + pctText.substringAfter('.') else null
                        Text(
                            text = intPart,
                            color = TrackerColors.TextPrimary,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 52.sp
                        )
                        if (fracPart != null) {
                            Text(
                                text = fracPart,
                                color = statusColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "—",
                            color = TrackerColors.TextMuted,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 52.sp
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = if (ready) "$totalAttended / $totalConducted" else "— / —",
                        color = if (ready) TrackerColors.TextSecondary else TrackerColors.TextSubtle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "classes attended",
                        color = TrackerColors.TextSubtle,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress bar — subtle 1px track that fills in with the data
        val progress by animateFloatAsState(
            targetValue = (overallPercentage / 100.0).toFloat().coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 700),
            label = "heroAttendanceProgress"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .clip(CircleShape)
                .background(TrackerColors.HairlineBorderLight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(1.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }
    }
}

/**
 * Two side-by-side panels (60:40 + gap) that always render at EQUAL height,
 * whatever their content:
 *
 * 1. Both panels are first subcomposed and measured ONCE with unbounded
 *    height to learn their natural heights.
 * 2. The row height becomes the taller of the two, capped by the incoming
 *    max height.
 * 3. Both panels are subcomposed AGAIN under fresh slot ids and measured
 *    exactly once with min = max = row height, so the shorter card stretches
 *    to match the taller one and neither ever clips.
 *
 * A Measurable may only be measure()d once per layout pass, and FlowRow
 * intrinsic heights under-report — hence probe + final subcompositions
 * instead of a two-pass measure() or IntrinsicSize.Min.
 */
@Composable
private fun StatRow(
    modifier: Modifier = Modifier,
    todayContent: @Composable () -> Unit,
    skipsContent: @Composable () -> Unit
) {
    SubcomposeLayout(modifier) { constraints ->
        val gapPx = 10.dp.roundToPx()
        val usable = constraints.maxWidth - gapPx
        val leftW = (usable * 0.6f).roundToInt()
        val rightW = usable - leftW

        // Pass 1 — probe compositions: natural heights from a real measure.
        val leftNatural = subcompose("probe-left") { todayContent() }.first().measure(
            Constraints(minWidth = leftW, maxWidth = leftW)
        ).height
        val rightNatural = subcompose("probe-right") { skipsContent() }.first().measure(
            Constraints(minWidth = rightW, maxWidth = rightW)
        ).height

        val rowHeight = maxOf(leftNatural, rightNatural)
            .let { if (constraints.hasBoundedHeight) it.coerceAtMost(constraints.maxHeight) else it }

        // Pass 2 — final compositions: fixed height (min = max = rowHeight),
        // never any infinite constraint, and each fresh Measurable is measured
        // exactly once.
        val leftPlaceable = subcompose("final-left") { todayContent() }.first().measure(
            Constraints(
                minWidth = leftW,
                maxWidth = leftW,
                minHeight = rowHeight,
                maxHeight = rowHeight
            )
        )
        val rightPlaceable = subcompose("final-right") { skipsContent() }.first().measure(
            Constraints(
                minWidth = rightW,
                maxWidth = rightW,
                minHeight = rowHeight,
                maxHeight = rowHeight
            )
        )

        layout(constraints.maxWidth, rowHeight) {
            leftPlaceable.placeRelative(0, 0)
            rightPlaceable.placeRelative(leftW + gapPx, 0)
        }
    }
}

/** Compact stat tile: skippable periods (or classes to recover). */
@Composable
private fun SkipsPanel(liveResponse: LiveAttendanceResponse?, hasData: Boolean, modifier: Modifier = Modifier) {
    val skips = liveResponse?.intelligence?.safeSkips
    val isSafe = skips?.status?.equals("Safe", ignoreCase = true) ?: true
    val accent = if (isSafe) TrackerColors.SafeEmerald else TrackerColors.DangerRose
    val waiting = !hasData || skips == null

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (waiting) TrackerColors.TextSubtle else accent)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        waiting -> "syncing"
                        isSafe -> "can skip"
                        else -> "must attend"
                    },
                    color = if (waiting) TrackerColors.TextSubtle else accent,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }

            // Big number
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when {
                        waiting -> "—"
                        isSafe -> "${skips.periods}"
                        else -> "${skips.classesNeededToRecover}"
                    },
                    color = if (waiting) TrackerColors.TextMuted else TrackerColors.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isSafe || waiting) "periods" else "classes",
                    color = TrackerColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Context
            Text(
                text = when {
                    waiting -> "Snapshot pending"
                    isSafe -> "≈ ${skips.days} days buffer"
                    else -> "To reach 75%"
                },
                color = TrackerColors.TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusPill(hasData: Boolean, overallPercentage: Double, statusColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = when {
                !hasData -> "syncing"
                overallPercentage >= 75.0 -> "safe"
                else -> "critical"
            },
            color = statusColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.SansSerif
        )
    }
}

// ---------------------------------------------------------------- today

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayPanel(
    liveResponse: LiveAttendanceResponse?,
    hasData: Boolean,
    modifier: Modifier = Modifier
) {
    val entries = liveResponse?.attendance?.today.orEmpty()


    val nowMs = System.currentTimeMillis()
    val parsed = entries.map { it to parseLooseDate(it.date) }
    val entry = parsed.firstOrNull { isSameCalendarDay(it.second, nowMs) }?.first
        ?: parsed.lastOrNull { it.second == null }?.first

    // fillMaxHeight: when StatRow stretches this card to its neighbour's
    // height, the title stays pinned to the top and the status sits at the
    // bottom instead of everything clumping in the middle.
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                text = "Today attendance status",
                color = TrackerColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )

            Spacer(modifier = Modifier.weight(1f))

            when {
                !hasData -> Text(
                    text = "Waiting for the latest snapshot…",
                    color = TrackerColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                entry == null -> Text(
                    text = "No attendance recorded for today yet.",
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                entry.badges.isEmpty() -> Text(
                    text = "No attendance recorded for today.",
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                else -> FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    entry.badges.forEach { badge ->
                        TodayBadgeChip(
                            subject = badge.subject,
                            status = badge.status
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayBadgeChip(subject: String, status: String) {
    val normalized = status.trim().uppercase().replace(" ", "")
    // Portal tokens are P/A runs: "P", "PP", "PPP"… all present; "A", "AA"… all
    // absent. Any A in the cell means the subject was skipped at least once.
    val wasAbsent = normalized.any { it == 'A' }
    val accent = if (wasAbsent) TrackerColors.DangerRose else TrackerColors.SafeEmerald

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = shortSubjectName(subject),
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
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** "DL & CO - Something long" → "DL & CO"; trims decoration but keeps the full short code. */
private fun shortSubjectName(raw: String): String =
    raw.split(" - ").first().trim().uppercase().ifBlank { "SUBJECT" }

// ---------------------------------------------------------------- fetch

@Composable
private fun FetchAttendanceButton(
    isLoading: Boolean,
    lastFetchDurationMs: Long?,
    scrapedAt: String?,
    hasData: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = !isLoading) { onClick() }
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = TrackerColors.TextMuted,
                strokeWidth = 1.5.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Fetching…",
                color = TrackerColors.TextMuted,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif
            )
        } else {
            Text(
                text = "↻",
                color = TrackerColors.TextSubtle,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Refresh attendance",
                color = TrackerColors.TextSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val durationText = lastFetchDurationMs?.let { formatResponseTime(it) }
            if (durationText != null) {
                Text(
                    text = "$durationText ⚡",
                    color = TrackerColors.SafeEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            if (hasData) {
                Text(
                    text = formatFetchedAt(scrapedAt),
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** "2.148 sec" from raw milliseconds. */
private fun formatResponseTime(ms: Long): String {
    val seconds = ms / 1000
    val millis = (ms % 1000).toInt()
    return "$seconds.${millis.toString().padStart(3, '0')} sec"
}

/** Time-of-day greeting for the home header. */
private fun timeGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "GOOD MORNING"
        in 12..16 -> "GOOD AFTERNOON"
        in 17..20 -> "GOOD EVENING"
        else -> "GOOD NIGHT"
    }
}

/** "INDELA NAVEEN" → "Indela Naveen" — greeting-case for the header name. */
private fun displayName(raw: String): String =
    raw.trim().split(Regex("\\s+"))
        .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        .ifBlank { "Student" }

/**
 * ISO scrapedAt → local, compact, human ("21 Sep, 10:12 pm"). Epoch-0 dates
 * (1970) from upstream clock glitches render as "—" instead of lying.
 */
private fun formatFetchedAt(scrapedAt: String?): String {
    if (scrapedAt.isNullOrBlank()) return "—"
    val parsed = runCatching {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        // scrapedAt is UTC (trailing 'Z') — parse as UTC, then render local below,
        // otherwise the UTC wall-clock leaks through as if it were local time.
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        format.isLenient = false
        format.parse(scrapedAt.trim().substringBefore('.')) ?: return "—"
    }.getOrNull() ?: return "—"

    val cal = java.util.Calendar.getInstance().apply { timeInMillis = parsed.time }
    if (cal.timeInMillis < 10_000L) return "—" // 1970 sentinel → invalid

    val month = java.text.SimpleDateFormat("MMM", java.util.Locale.US)
        .format(cal.time).uppercase(java.util.Locale.US)
    val hour12 = cal.get(java.util.Calendar.HOUR)
    val hour = if (hour12 == 0) 12 else hour12
    val minute = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
    val amPm = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "am" else "pm"
    return "${cal.get(java.util.Calendar.DAY_OF_MONTH)} $month, $hour:$minute $amPm"
}

// ---------------------------------------------------------------- date helpers

/** Best-effort parse of the various date formats the server may send. Returns epoch ms or null. */
private fun parseLooseDate(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim().uppercase()

    // Register dates are bare "DD/MM" (no year) — resolve against the current year.
    // Anchor to today first; never Calendar.clear() (on ART it forces a recompute
    // from zeroed fields and lands back in 1970).
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

private fun isSameCalendarDay(aMs: Long?, bMs: Long): Boolean {
    if (aMs == null) return false
    val a = java.util.Calendar.getInstance().apply { timeInMillis = aMs }
    val b = java.util.Calendar.getInstance().apply { timeInMillis = bMs }
    return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
}

// ---------------------------------------------------------------- subjects

@Composable
private fun SubjectsListCard(subjects: List<UiSubjectAttendance>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SUBJECTS",
                color = TrackerColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(
                text = "${subjects.size} registered",
                color = TrackerColors.TextSubtle,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        subjects.forEachIndexed { index, subject ->
            val statusColor = when {
                subject.percentage >= 80.0 -> TrackerColors.SafeEmerald
                subject.percentage >= 75.0 -> TrackerColors.WarningAmber
                else -> TrackerColors.DangerRose
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.name,
                        color = TrackerColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${subject.attended}/${subject.conducted}",
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = formatPercentage(subject.percentage),
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.End
                )
            }

            if (index != subjects.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TrackerColors.HairlineBorder)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- nav bar

@Composable
fun ExpressiveFloatingPillNavBar(
    selectedTab: MainNavTab,
    onTabSelected: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = MainNavTab.entries
    val selectedIndex = selectedTab.ordinal

    val tabWidth = 80.dp
    val tabGap = 2.dp

    // Smooth physics highlight that glides between tabs
    val indicatorOffset by animateDpAsState(
        targetValue = (tabWidth + tabGap) * selectedIndex,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f),
        label = "navIndicatorOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = TrackerColors.SurfaceDark,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, TrackerColors.HairlineBorder)
        ) {
            Box(modifier = Modifier.padding(4.dp)) {
                // Sliding highlight capsule that physically glides across the bar
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .size(width = tabWidth, height = 38.dp)
                        .clip(CircleShape)
                        .background(TrackerColors.SurfaceElevated)
                        .border(1.dp, TrackerColors.HairlineBorderLight, CircleShape)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(tabGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = tab == selectedTab
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) TrackerColors.PrimaryWhite else TrackerColors.TextMuted,
                            animationSpec = tween(180),
                            label = "navTabColor-${tab.name}"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.94f,
                            animationSpec = spring(dampingRatio = 0.88f, stiffness = 450f),
                            label = "navIconScale-${tab.name}"
                        )

                        Box(
                            modifier = Modifier
                                .width(tabWidth)
                                .height(38.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTabSelected(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            // Unselected: icon. Selected: label only, centered — long
                            // labels like TIMETABLE get the full capsule width instead
                            // of squeezing in next to the icon.
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isSelected,
                                enter = fadeIn(animationSpec = tween(140)) + expandHorizontally(
                                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f),
                                    expandFrom = Alignment.CenterHorizontally
                                ),
                                exit = fadeOut(animationSpec = tween(90)) + shrinkHorizontally(
                                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f),
                                    shrinkTowards = Alignment.CenterHorizontally
                                )
                            ) {
                                NavTabIcon(tab = tab, color = contentColor, scale = iconScale)
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(
                                    animationSpec = tween(160, delayMillis = 80)
                                ) + expandHorizontally(
                                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f),
                                    expandFrom = Alignment.CenterHorizontally
                                ),
                                exit = fadeOut(
                                    animationSpec = tween(90)
                                ) + shrinkHorizontally(
                                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f),
                                    shrinkTowards = Alignment.CenterHorizontally
                                )
                            ) {
                                Text(
                                    text = tab.label,
                                    color = contentColor,
                                    // Longer labels (ATTENDANCE, TIMETABLE) get a smaller
                                    // size so they still fit the capsule at larger
                                    // system font scales.
                                    fontSize = when {
                                        tab.label.length >= 10 -> 9.sp
                                        tab.label.length >= 9 -> 10.sp
                                        else -> 10.5.sp
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTabIcon(tab: MainNavTab, color: Color, scale: Float = 1f) {
    Canvas(
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        when (tab) {
            MainNavTab.HOME -> {
                // 2x2 dashboard grid
                val cell = size.width * 0.42f
                val gap = size.width - cell * 2f
                val r = CornerRadius(cell * 0.32f, cell * 0.32f)
                drawRoundRect(color, Offset.Zero, Size(cell, cell), r)
                drawRoundRect(color, Offset(cell + gap, 0f), Size(cell, cell), r)
                drawRoundRect(color, Offset(0f, cell + gap), Size(cell, cell), r)
                drawRoundRect(color, Offset(cell + gap, cell + gap), Size(cell, cell), r)
            }

            MainNavTab.ATTENDANCE -> {
                // Attendance sheet: document with title bar and list lines
                val w = size.width
                val h = size.height
                val strokeW = w * 0.085f
                val bodyTop = h * 0.16f

                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, bodyTop),
                    size = Size(w, h - bodyTop),
                    cornerRadius = CornerRadius(w * 0.2f, w * 0.2f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                // Title band
                drawLine(color, Offset(w * 0.24f, h * 0.34f), Offset(w * 0.76f, h * 0.34f), strokeW, StrokeCap.Round)
                // List lines
                drawLine(color, Offset(w * 0.24f, h * 0.54f), Offset(w * 0.76f, h * 0.54f), strokeW * 0.8f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.24f, h * 0.72f), Offset(w * 0.6f, h * 0.72f), strokeW * 0.8f, StrokeCap.Round)
            }

            MainNavTab.TIMETABLE -> {
                // Calendar with binder rings + a today dot
                val w = size.width
                val h = size.height
                val strokeW = w * 0.085f
                val bodyTop = h * 0.16f

                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, bodyTop),
                    size = Size(w, h - bodyTop),
                    cornerRadius = CornerRadius(w * 0.2f, w * 0.2f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawLine(color, Offset(w * 0.3f, 0f), Offset(w * 0.3f, h * 0.24f), strokeW, StrokeCap.Round)
                drawLine(color, Offset(w * 0.7f, 0f), Offset(w * 0.7f, h * 0.24f), strokeW, StrokeCap.Round)
                drawLine(color, Offset(0f, h * 0.42f), Offset(w, h * 0.42f), strokeW)
                drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.3f, h * 0.68f))
                drawLine(color, Offset(w * 0.48f, h * 0.68f), Offset(w * 0.8f, h * 0.68f), strokeW, StrokeCap.Round)
            }

            MainNavTab.PROFILE -> {
                // Profile silhouette: head circle + shoulder curve
                val w = size.width
                val h = size.height
                val strokeW = w * 0.09f

                // Head
                drawCircle(
                    color = color,
                    radius = w * 0.22f,
                    center = Offset(w * 0.5f, h * 0.28f),
                    style = Stroke(width = strokeW)
                )

                // Shoulders
                val path = Path().apply {
                    moveTo(w * 0.14f, h * 0.90f)
                    quadraticTo(w * 0.16f, h * 0.62f, w * 0.5f, h * 0.62f)
                    quadraticTo(w * 0.84f, h * 0.62f, w * 0.86f, h * 0.90f)
                }
                drawPath(path = path, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
            }
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
fun HeaderBar(
    isLoggedIn: Boolean,
    onLogout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackerColors.PureBlack)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Wordmark
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "VIIT",
                    color = TrackerColors.TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "tracker",
                    color = TrackerColors.TextSubtle,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }

            if (isLoggedIn) {
                Text(
                    text = "Sign out",
                    color = TrackerColors.TextSubtle,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier
                        .clickable { onLogout() }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        }
    }
}
