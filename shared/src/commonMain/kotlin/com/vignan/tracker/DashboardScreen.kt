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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MainNavTab(val label: String) {
    HOME("HOME"),
    TIMETABLE("TIMETABLE")
}

@Composable
fun DashboardScreen(
    data: AttendanceResponse?,
    liveResponse: LiveAttendanceResponse? = null,
    isRefreshing: Boolean = false,
    lastFetchDurationMs: Long? = null,
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 86.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedNavTab) {
                MainNavTab.HOME -> {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SkippablePeriodsCard(
                                liveResponse = liveResponse,
                                hasData = hasData,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            AttendanceRingCard(
                                percentage = overallPercentage,
                                hasData = hasData,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }

                    item { GreetingCard(rollNumber = data?.rollNumber.orEmpty()) }

                    item {
                        PeriodsLedgerCard(
                            totalAttended = totalAttended,
                            totalConducted = totalConducted,
                            hasData = hasData
                        )
                    }

                    item { TodayAttendanceCard(liveResponse = liveResponse, hasData = hasData) }

                    item {
                        FetchServerButton(
                            serverLabel = "Fastest Server",
                            isLoading = isRefreshing,
                            onClick = onFetchClick
                        )
                    }

                    item {
                        FetchServerButton(
                            serverLabel = "Flexible Server",
                            isLoading = isRefreshing,
                            onClick = onFetchClick
                        )
                    }

                    item {
                        LastUpdatedFooter(
                            scrapedAt = liveResponse?.scrapedAt,
                            fetchDurationMs = lastFetchDurationMs,
                            hasData = hasData
                        )
                    }
                }

                MainNavTab.TIMETABLE -> {
                    item {
                        TimetableScreen(liveResponse = liveResponse)
                    }
                }
            }

            // Bottom Footer Badge
            item {
                Spacer(modifier = Modifier.height(4.dp))
                FooterBadge()
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

// ---------------------------------------------------------------- home cards

@Composable
private fun SkippablePeriodsCard(
    liveResponse: LiveAttendanceResponse?,
    hasData: Boolean,
    modifier: Modifier = Modifier
) {
    val skips = liveResponse?.intelligence?.safeSkips
    val isSafe = skips?.status?.equals("Safe", ignoreCase = true) ?: true
    val accent = if (isSafe) TrackerColors.SafeEmerald else TrackerColors.DangerRose
    val bigNumber = when {
        !hasData || skips == null -> null
        isSafe -> skips.periods
        else -> skips.classesNeededToRecover
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = if (isSafe) "Periods can skip" else "Classes needed",
                color = TrackerColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = bigNumber?.toString() ?: "—",
                    color = TrackerColors.TextPrimary,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "⚡",
                    color = accent,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSafe) {
                    MiniStatTile(
                        value = skips?.days?.toString() ?: "—",
                        label = "days",
                        accent = accent
                    )
                    MiniStatTile(
                        value = skips?.periods?.toString() ?: "—",
                        label = "periods",
                        accent = accent
                    )
                } else {
                    MiniStatTile(
                        value = skips?.classesNeededToRecover?.toString() ?: "—",
                        label = "attend",
                        accent = accent
                    )
                    MiniStatTile(
                        value = "${skips?.projectedPercentage?.toInt() ?: 0}%",
                        label = "projected",
                        accent = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStatTile(value: String, label: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TrackerColors.PureBlack.copy(alpha = 0.45f))
            .border(1.dp, TrackerColors.HairlineBorderLight, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            color = accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = TrackerColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
private fun AttendanceRingCard(
    percentage: Double,
    hasData: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        !hasData -> TrackerColors.TextMuted
        percentage >= 80.0 -> TrackerColors.SafeEmerald
        percentage >= 75.0 -> TrackerColors.WarningAmber
        else -> TrackerColors.DangerRose
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Present attendance",
                color = TrackerColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(118.dp)) {
                    val strokeW = 11.dp.toPx()
                    val inset = strokeW / 2 + 1.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val arcTopLeft = Offset(inset, inset)

                    // Track
                    drawArc(
                        color = TrackerColors.TextSubtle.copy(alpha = 0.35f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    // Progress
                    if (hasData) {
                        drawArc(
                            color = statusColor,
                            startAngle = -90f,
                            sweepAngle = 360f * (percentage / 100.0).toFloat().coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                }
                Text(
                    text = if (hasData) formatPercentage(percentage) else "—",
                    color = if (hasData) TrackerColors.TextPrimary else TrackerColors.TextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun GreetingCard(rollNumber: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Hi, ",
            color = TrackerColors.TextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif
        )
        Text(
            text = rollNumber.ifBlank { "—" },
            color = TrackerColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PeriodsLedgerCard(totalAttended: Int, totalConducted: Int, hasData: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LedgerTile(label = "Number of periods attended", value = if (hasData) "$totalAttended" else "—")
        LedgerTile(label = "Number of periods held", value = if (hasData) "$totalConducted" else "—")
    }
}

@Composable
private fun LedgerTile(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TrackerColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(TrackerColors.PureBlack)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = value,
                color = TrackerColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayAttendanceCard(liveResponse: LiveAttendanceResponse?, hasData: Boolean) {
    val today = liveResponse?.attendance?.today?.maxByOrNull { it.date }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = "Today attendance status",
                color = TrackerColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            when {
                !hasData -> Text(
                    text = "Waiting for the latest attendance snapshot…",
                    color = TrackerColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                today == null || today.badges.isEmpty() -> Text(
                    text = "No attendance recorded for today.",
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                else -> FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    today.badges.forEach { badge ->
                        TodayBadgeChip(
                            label = "${badge.subject.substringBefore(" ").trim().uppercase()}: ${badge.status}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayBadgeChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TrackerColors.SafeEmerald.copy(alpha = 0.14f))
            .border(1.dp, TrackerColors.SafeEmerald.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = TrackerColors.SafeEmerald,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun FetchServerButton(serverLabel: String, isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceElevated)
            .border(1.dp, TrackerColors.HairlineBorderLight, RoundedCornerShape(12.dp))
            .clickable(enabled = !isLoading) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = TrackerColors.TextPrimary,
                    strokeWidth = 1.5.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FETCHING…",
                    color = TrackerColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Fetch Attendance",
                    color = TrackerColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = serverLabel,
                    color = TrackerColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
private fun LastUpdatedFooter(scrapedAt: String?, fetchDurationMs: Long?, hasData: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Last updated: ${formatScrapedAt(scrapedAt)}",
            color = TrackerColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif
        )
        Spacer(modifier = Modifier.height(3.dp))
        if (fetchDurationMs != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Response time: ",
                    color = TrackerColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "${formatResponseTime(fetchDurationMs)} ⚡",
                    color = TrackerColors.SafeEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else if (hasData) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Cached snapshot shown",
                    color = TrackerColors.TextSubtle,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/** Renders the server's scrapedAt ("21/09/2026, 05:55:13 pm") in a compact form; falls back to raw. */
private fun formatScrapedAt(scrapedAt: String?): String {
    if (scrapedAt.isNullOrBlank()) return "—"
    val cleaned = scrapedAt.trim()
    return runCatching {
        val inFormat = java.text.SimpleDateFormat("dd/MM/yyyy, hh:mm:ss a", java.util.Locale.US)
        val outFormat = java.text.SimpleDateFormat("dd/MM/yyyy, hh:mm a", java.util.Locale.US)
        val date = inFormat.parse(cleaned.uppercase()) ?: return cleaned
        outFormat.format(date)
    }.getOrDefault(cleaned)
}

private fun formatResponseTime(ms: Long): String {
    val seconds = ms / 1000
    val millis = (ms % 1000).toInt()
    return "$seconds.${millis.toString().padStart(3, '0')} sec"
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
