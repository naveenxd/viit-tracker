package com.vignan.tracker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class MainNavTab(val label: String) {
    COURSES("COURSES"),
    TIMETABLE("TIMETABLE")
}

@Composable
fun DashboardSkeleton() {
    // Diagonal light sweep shared by every ghost element
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            TrackerColors.SurfaceDark,
            TrackerColors.SurfaceElevated.copy(alpha = 0.85f),
            TrackerColors.SurfaceDark
        ),
        start = Offset(translateAnim, translateAnim * 0.35f),
        end = Offset(translateAnim + 420f, translateAnim * 0.35f + 420f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        StaggeredAppear(index = 0) { SkeletonHeroCard(brush) }

        Spacer(modifier = Modifier.height(14.dp))

        repeat(4) { i ->
            StaggeredAppear(index = i + 1) { SkeletonSubjectRow(brush) }
            if (i < 3) Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        LoadingCaption()
    }
}

/** Fades/slides each skeleton block in with a per-index delay. */
@Composable
private fun StaggeredAppear(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "skeletonStagger-$index"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 24.dp.toPx()
        }
    ) {
        content()
    }
}

@Composable
private fun GhostBar(modifier: Modifier, brush: Brush, cornerRadius: Dp = 6.dp) {
    Box(modifier.clip(RoundedCornerShape(cornerRadius)).background(brush))
}

/** Hero ghost with internal detail bars mirroring the real card's anatomy. */
@Composable
private fun SkeletonHeroCard(brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(brush)
        )

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    GhostBar(modifier = Modifier.width(110.dp).height(12.dp), brush)
                    Spacer(modifier = Modifier.height(6.dp))
                    GhostBar(modifier = Modifier.width(72.dp).height(8.dp), brush, cornerRadius = 4.dp)
                }
                GhostBar(
                    modifier = Modifier.width(88.dp).height(20.dp),
                    brush,
                    cornerRadius = 10.dp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            GhostBar(modifier = Modifier.width(132.dp).height(42.dp), brush, cornerRadius = 8.dp)

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(TrackerColors.PureBlack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            GhostBar(
                modifier = Modifier.fillMaxWidth().height(38.dp),
                brush,
                cornerRadius = 8.dp
            )
        }
    }
}

/** Subject-row ghost matching MinimalSubjectRow's real layout. */
@Composable
private fun SkeletonSubjectRow(brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(10.dp))
                .background(brush)
        )

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GhostBar(modifier = Modifier.width(64.dp).height(8.dp), brush, cornerRadius = 4.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        GhostBar(modifier = Modifier.width(42.dp).height(8.dp), brush, cornerRadius = 4.dp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    GhostBar(modifier = Modifier.width(150.dp).height(12.dp), brush)
                }
                GhostBar(modifier = Modifier.width(46.dp).height(16.dp), brush, cornerRadius = 4.dp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(TrackerColors.PureBlack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }
        }
    }
}

@Composable
private fun LoadingCaption() {
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
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "FETCHING LATEST DATA",
            color = TrackerColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        )
    }
}

@Composable
fun DashboardScreen(
    data: AttendanceResponse,
    liveResponse: LiveAttendanceResponse? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: SubjectFilter,
    onFilterSelect: (SubjectFilter) -> Unit
) {
    var selectedNavTab by remember { mutableStateOf(MainNavTab.COURSES) }

    val totalAttended = data.subjects.sumOf { it.attended }
    val totalConducted = data.subjects.sumOf { it.conducted }
    val overallInsight = calculateMargin(totalAttended, totalConducted)

    val filteredSubjects = data.subjects.filter { subject ->
        val matchesSearch = subject.name.contains(searchQuery, ignoreCase = true) ||
                subject.code.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            SubjectFilter.ALL -> true
            SubjectFilter.AT_RISK -> subject.percentage < 75.0
            SubjectFilter.SAFE -> subject.percentage >= 75.0
        }
        matchesSearch && matchesFilter
    }

    val atRiskCount = data.subjects.count { it.percentage < 75.0 }
    val safeCount = data.subjects.count { it.percentage >= 75.0 }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 86.dp)
        ) {
            when (selectedNavTab) {
                MainNavTab.COURSES -> {
                    item {
                        HeroTerminalCard(
                            studentName = data.studentName,
                            rollNumber = data.rollNumber,
                            branch = liveResponse?.profile?.branch.orEmpty(),
                            semester = liveResponse?.profile?.semester ?: "",
                            overallPercentage = data.overallPercentage,
                            totalAttended = totalAttended,
                            totalConducted = totalConducted,
                            overallInsight = overallInsight,
                            liveResponse = liveResponse
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    item {
                        FilterToolbar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = onSearchQueryChange,
                            selectedFilter = selectedFilter,
                            onFilterSelect = onFilterSelect,
                            totalCount = data.subjects.size,
                            atRiskCount = atRiskCount,
                            safeCount = safeCount
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (filteredSubjects.isEmpty()) {
                        item {
                            EmptyStateView("NO MATCHING COURSES", "Adjust search query or active filter tab")
                        }
                    } else {
                        items(filteredSubjects) { subject ->
                            MinimalSubjectRow(subject = subject)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
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
                Spacer(modifier = Modifier.height(12.dp))
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
            MainNavTab.COURSES -> {
                // 2x2 course grid
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

@Composable
fun HeaderBar(
    isLoggedIn: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TrackerColors.SurfaceDark)
                            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(6.dp))
                            .clickable(enabled = !isLoading) { onRefresh() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = TrackerColors.TextPrimary,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Text(
                                text = "SYNC",
                                color = TrackerColors.TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

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

@Composable
private fun HeroTerminalCard(
    studentName: String,
    rollNumber: String,
    branch: String,
    semester: String,
    overallPercentage: Double,
    totalAttended: Int,
    totalConducted: Int,
    overallInsight: AttendanceInsight,
    liveResponse: LiveAttendanceResponse?
) {
    val statusColor = when {
        overallPercentage >= 80.0 -> TrackerColors.SafeEmerald
        overallPercentage >= 75.0 -> TrackerColors.WarningAmber
        else -> TrackerColors.DangerRose
    }

    val safeSkips = liveResponse?.intelligence?.safeSkips

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = studentName.ifBlank { "STUDENT" }.uppercase(),
                        color = TrackerColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            rollNumber.takeIf { it.isNotBlank() },
                            branch.takeIf { it.isNotBlank() },
                            semester.takeIf { it.isNotBlank() }
                        ).joinToString("  •  ").ifBlank { rollNumber },
                        color = TrackerColors.TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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
                            text = if (overallPercentage >= 75.0) "SAFE ZONE" else "CRITICAL",
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
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
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$totalAttended / $totalConducted",
                        color = TrackerColors.TextPrimary,
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

            Spacer(modifier = Modifier.height(12.dp))

            // Intelligence Safe Skips Callout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TrackerColors.PureBlack)
                    .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = when {
                        safeSkips != null -> {
                            if (safeSkips.status.equals("Safe", ignoreCase = true)) {
                                "Safe Skips: ${safeSkips.days} days (${safeSkips.periods} periods) buffer remaining."
                            } else {
                                "Action Needed: Attend next ${safeSkips.classesNeededToRecover} classes to reach 75%."
                            }
                        }
                        overallInsight is AttendanceInsight.Safe -> {
                            if (overallInsight.canSkipClasses > 0)
                                "Buffer: Can safely skip ${overallInsight.canSkipClasses} total classes."
                            else
                                "Boundary: Maintain attendance to prevent drop below 75%."
                        }
                        overallInsight is AttendanceInsight.AtRisk -> {
                            "Action: Attend next ${overallInsight.requiredClasses} consecutive classes."
                        }
                        else -> "Target: 75% attendance threshold"
                    },
                    color = TrackerColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Serif
                )
            }
        }
    }
}

@Composable
private fun FilterToolbar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: SubjectFilter,
    onFilterSelect: (SubjectFilter) -> Unit,
    totalCount: Int,
    atRiskCount: Int,
    safeCount: Int
) {
    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    text = "Filter course name or code...",
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = TrackerColors.TextPrimary
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TrackerColors.SurfaceDark,
                unfocusedContainerColor = TrackerColors.SurfaceDark,
                focusedBorderColor = TrackerColors.HairlineBorderLight,
                unfocusedBorderColor = TrackerColors.HairlineBorder,
                focusedTextColor = TrackerColors.TextPrimary,
                unfocusedTextColor = TrackerColors.TextPrimary
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SegmentChip(
                label = "ALL ($totalCount)",
                isSelected = selectedFilter == SubjectFilter.ALL,
                onClick = { onFilterSelect(SubjectFilter.ALL) }
            )

            SegmentChip(
                label = "CRITICAL ($atRiskCount)",
                isSelected = selectedFilter == SubjectFilter.AT_RISK,
                accentColor = TrackerColors.DangerRose,
                onClick = { onFilterSelect(SubjectFilter.AT_RISK) }
            )

            SegmentChip(
                label = "SAFE ($safeCount)",
                isSelected = selectedFilter == SubjectFilter.SAFE,
                accentColor = TrackerColors.SafeEmerald,
                onClick = { onFilterSelect(SubjectFilter.SAFE) }
            )
        }
    }
}

@Composable
private fun SegmentChip(
    label: String,
    isSelected: Boolean,
    accentColor: Color = TrackerColors.PrimaryWhite,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) TrackerColors.SurfaceElevated else TrackerColors.PureBlack)
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor.copy(alpha = 0.6f) else TrackerColors.HairlineBorder,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) accentColor else TrackerColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun MinimalSubjectRow(subject: UiSubjectAttendance) {
    val insight = calculateMargin(subject.attended, subject.conducted)
    val percentageColor = when {
        subject.percentage >= 80.0 -> TrackerColors.SafeEmerald
        subject.percentage >= 75.0 -> TrackerColors.WarningAmber
        else -> TrackerColors.DangerRose
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = subject.code.ifBlank { "COURSE" },
                            color = TrackerColors.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${subject.attended}/${subject.conducted} classes",
                            color = TrackerColors.TextSubtle,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subject.name,
                        color = TrackerColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatPercentage(subject.percentage),
                        color = percentageColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = when (insight) {
                            is AttendanceInsight.Safe -> if (insight.canSkipClasses > 0) "+${insight.canSkipClasses} skips" else "0 margin"
                            is AttendanceInsight.AtRisk -> "-${insight.requiredClasses} needed"
                        },
                        color = when (insight) {
                            is AttendanceInsight.Safe -> TrackerColors.SafeEmerald
                            is AttendanceInsight.AtRisk -> TrackerColors.DangerRose
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(TrackerColors.HairlineBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((subject.percentage / 100.0).toFloat().coerceIn(0f, 1f))
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(percentageColor)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = TrackerColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(
                text = subtitle,
                color = TrackerColors.TextSubtle,
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
