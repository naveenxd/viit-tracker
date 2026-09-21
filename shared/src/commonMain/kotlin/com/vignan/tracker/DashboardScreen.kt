package com.vignan.tracker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class MainNavTab(val label: String) {
    HOME("HOME"),
    TIMETABLE("TIMETABLE")
}

@Composable
fun DashboardScreen(
    data: AttendanceResponse?,
    liveResponse: LiveAttendanceResponse? = null,
    isRefreshing: Boolean = false,
    onFetchClick: () -> Unit = {}
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
                            hasData = hasData
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

                    item { FetchAttendanceButton(isLoading = isRefreshing, onClick = onFetchClick) }
                }

                MainNavTab.TIMETABLE -> {
                    item {
                        TimetableScreen(liveResponse = liveResponse)
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
    hasData: Boolean
) {
    val statusColor = when {
        !hasData -> TrackerColors.TextMuted
        overallPercentage >= 80.0 -> TrackerColors.SafeEmerald
        overallPercentage >= 75.0 -> TrackerColors.WarningAmber
        else -> TrackerColors.DangerRose
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            // Identity: name + status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = studentName.ifBlank { "STUDENT" }.uppercase(),
                    color = TrackerColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(10.dp))
                StatusPill(
                    hasData = hasData,
                    overallPercentage = overallPercentage,
                    statusColor = statusColor
                )
            }

            // Roll • branch • semester
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = listOfNotNull(
                    rollNumber.takeIf { it.isNotBlank() },
                    branch.takeIf { it.isNotBlank() },
                    semester.takeIf { it.isNotBlank() }
                ).joinToString("  •  ").ifBlank { "—" },
                color = TrackerColors.TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Percentage + attended/conducted
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (hasData) {
                        val pctText = formatPercentage(overallPercentage)
                        val intPart = pctText.substringBefore('.')
                        val fracPart = if ('.' in pctText) "." + pctText.substringAfter('.') else null
                        Text(
                            text = intPart,
                            color = TrackerColors.TextPrimary,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        if (fracPart != null) {
                            Text(
                                text = fracPart,
                                color = statusColor,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "—",
                            color = TrackerColors.TextMuted,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (hasData) "$totalAttended / $totalConducted" else "— / —",
                        color = if (hasData) TrackerColors.TextPrimary else TrackerColors.TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ATTENDED CLASSES",
                        color = TrackerColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(TrackerColors.HairlineBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((overallPercentage / 100.0).toFloat().coerceIn(0f, 1f))
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
        }
    }
}

/**
 * Two side-by-side panels (60:40 + gap) whose heights always match.
 * The LEFT panel is measured with unbounded height so content (a FlowRow of
 * chips) fully determines the row height — FlowRow's intrinsic measurement
 * under-reports and clips rows when used with IntrinsicSize.Min.
 */
@Composable
private fun StatRow(
    modifier: Modifier = Modifier,
    todayContent: @Composable () -> Unit,
    skipsContent: @Composable () -> Unit
) {
    Layout(
        content = {
            // NOTE: no wrapper Boxes — Box strips min constraints from its
            // children by default, which is exactly what broke height matching.
            todayContent()
            skipsContent()
        }
    ) { measurables, constraints ->
        val gapPx = 10.dp.roundToPx()
        val usable = constraints.maxWidth - gapPx
        val leftW = (usable * 0.6f).roundToInt()
        val rightW = usable - leftW

        val leftPlaceable = measurables[0].measure(
            Constraints(minWidth = leftW, maxWidth = leftW)
        )
        val rightPlaceable = measurables[1].measure(
            Constraints(
                minWidth = rightW,
                maxWidth = rightW,
                minHeight = leftPlaceable.height,
                maxHeight = leftPlaceable.height
            )
        )

        layout(constraints.maxWidth, leftPlaceable.height) {
            leftPlaceable.placeRelative(0, 0)
            rightPlaceable.placeRelative(leftW + gapPx, 0)
        }
    }
}

/** Compact stat tile: skippable periods (or classes to recover), accent-edged. */
@Composable
private fun SkipsPanel(liveResponse: LiveAttendanceResponse?, hasData: Boolean, modifier: Modifier = Modifier) {
    val skips = liveResponse?.intelligence?.safeSkips
    val isSafe = skips?.status?.equals("Safe", ignoreCase = true) ?: true
    val accent = if (isSafe) TrackerColors.SafeEmerald else TrackerColors.DangerRose
    val waiting = !hasData || skips == null

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            // Accent edge, same language as the timetable subject bars
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (waiting) TrackerColors.HairlineBorder else accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡ ",
                        color = if (waiting) TrackerColors.TextMuted else accent,
                        fontSize = 10.sp
                    )
                    Text(
                        text = when {
                            waiting -> "SYNCING"
                            isSafe -> "CAN SKIP"
                            else -> "ATTEND"
                        },
                        color = if (waiting) TrackerColors.TextMuted else accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = when {
                            waiting -> "—"
                            isSafe -> "${skips.periods}"
                            else -> "${skips.classesNeededToRecover}"
                        },
                        color = if (waiting) TrackerColors.TextMuted else TrackerColors.TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isSafe || waiting) "periods" else "classes",
                        color = TrackerColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = when {
                        waiting -> "snapshot pending"
                        isSafe -> "≈ ${skips.days} days buffer"
                        else -> "to reach 75%"
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
}

@Composable
private fun StatusPill(hasData: Boolean, overallPercentage: Double, statusColor: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(statusColor.copy(alpha = 0.12f))
            .border(1.dp, statusColor.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when {
                    !hasData -> "SYNCING"
                    overallPercentage >= 75.0 -> "SAFE ZONE"
                    else -> "CRITICAL"
                },
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
        }
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

    // Pick today's entry by parsing its date; fall back to the most recent one.
    val nowMs = System.currentTimeMillis()
    val todaysEntry = entries.firstOrNull { isSameCalendarDay(parseLooseDate(it.date), nowMs) }
    val latestEntry = entries
        .mapNotNull { e -> parseLooseDate(e.date)?.let { e to it } }
        .maxByOrNull { it.second }?.first
        ?: entries.lastOrNull()
    val entry = todaysEntry ?: latestEntry

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = "Today attendance status",
                color = TrackerColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(10.dp))

            when {
                !hasData -> Text(
                    text = "Waiting for the latest snapshot…",
                    color = TrackerColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                entry == null || entry.badges.isEmpty() -> Text(
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
private fun FetchAttendanceButton(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(10.dp))
            .clickable(enabled = !isLoading) { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    color = TrackerColors.TextMuted,
                    strokeWidth = 1.5.dp
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "FETCHING…",
                    color = TrackerColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            } else {
                Text(
                    text = "↻  ",
                    color = TrackerColors.TextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = "FETCH ATTENDANCE",
                    color = TrackerColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
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

// ---------------------------------------------------------------- nav bar

@Composable
fun ExpressiveFloatingPillNavBar(
    selectedTab: MainNavTab,
    onTabSelected: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = MainNavTab.entries
    val selectedIndex = selectedTab.ordinal

    val tabWidth = 118.dp
    val tabGap = 2.dp

    // Spring-physics highlight that glides between tabs
    val indicatorOffset by animateDpAsState(
        targetValue = (tabWidth + tabGap) * selectedIndex,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
        label = "navIndicatorOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = TrackerColors.SurfaceDark,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, TrackerColors.HairlineBorder)
        ) {
            Box(modifier = Modifier.padding(5.dp)) {
                // Sliding highlight capsule behind the tabs
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .size(width = tabWidth, height = 40.dp)
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
                            animationSpec = tween(220),
                            label = "navTabColor-${tab.name}"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.92f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
                            label = "navIconScale-${tab.name}"
                        )

                        Box(
                            modifier = Modifier
                                .width(tabWidth)
                                .height(40.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTabSelected(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                NavTabIcon(tab = tab, color = contentColor, scale = iconScale)
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = tab.label,
                                    color = contentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp,
                                    maxLines = 1
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
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
fun HeaderBar(
    isLoggedIn: Boolean,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackerColors.PureBlack)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TrackerColors.PrimaryWhite)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "VIIT",
                        color = TrackerColors.PureBlack,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Attendance Tracker",
                    color = TrackerColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
            }

            if (isLoggedIn) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TrackerColors.SurfaceDark)
                        .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(6.dp))
                        .clickable { onLogout() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "LOGOUT",
                        color = TrackerColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TrackerColors.SafeEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ONLINE",
                        color = TrackerColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TrackerColors.HairlineBorder)
        )
    }
}
