package com.vignan.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(
    data: AttendanceResponse,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: SubjectFilter,
    onFilterSelect: (SubjectFilter) -> Unit
) {
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        item {
            HeroTerminalCard(
                studentName = data.studentName,
                rollNumber = data.rollNumber,
                overallPercentage = data.overallPercentage,
                totalAttended = totalAttended,
                totalConducted = totalConducted,
                atRiskCount = atRiskCount,
                safeCount = safeCount,
                overallInsight = overallInsight
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NO MATCHING COURSES",
                            color = TrackerColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Adjust search query or active filter tab",
                            color = TrackerColors.TextSubtle,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        } else {
            items(filteredSubjects) { subject ->
                MinimalSubjectRow(subject = subject)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Bottom Footer Badge
        item {
            Spacer(modifier = Modifier.height(12.dp))
            FooterBadge()
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
                    text = "Attendence Tracker",
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
    overallPercentage: Double,
    totalAttended: Int,
    totalConducted: Int,
    atRiskCount: Int,
    safeCount: Int,
    overallInsight: AttendanceInsight
) {
    val statusColor = when {
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
                        text = rollNumber,
                        color = TrackerColors.TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

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

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${overallPercentage.toInt()}",
                        color = TrackerColors.TextPrimary,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = ".${((overallPercentage - overallPercentage.toInt()) * 10).toInt()}%",
                        color = statusColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TrackerColors.PureBlack)
                    .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = when (overallInsight) {
                        is AttendanceInsight.Safe -> {
                            if (overallInsight.canSkipClasses > 0)
                                "Buffer: Can safely skip ${overallInsight.canSkipClasses} total classes."
                            else
                                "Boundary: Maintain attendance to prevent drop below 75%."
                        }
                        is AttendanceInsight.AtRisk -> {
                            "Action: Attend next ${overallInsight.requiredClasses} consecutive classes to reach 75%."
                        }
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
                        text = "${subject.percentage.toInt()}%",
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
