package com.vignan.tracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home screen widget for attendance tracker app.
 * Supports multiple widget sizes: 4:2 (larger) and 3:2 (compact).
 */

data class WidgetAttendanceData(
    val studentName: String,
    val rollNumber: String,
    val overallPercentage: Double,
    val safeSkips: SkipsInfo?,
    val todayAttendance: List<Badge>?,
    val totalAttended: Int,
    val totalConducted: Int,
    val isLoading: Boolean = false
)

/**
 * Composable that automatically selects the appropriate widget size
 * based on available space and widget configuration
 */
@Composable
fun HomeWidget(
    data: WidgetAttendanceData?,
    widgetSize: WidgetSize = WidgetSize.SIZE_4_2,
    onTap: () -> Unit = {}
) {
    when (widgetSize) {
        WidgetSize.SIZE_4_2 -> Widget4x2(data = data, onTap = onTap)
        WidgetSize.SIZE_3_2 -> Widget3x2(data = data, onTap = onTap)
    }
}

enum class WidgetSize {
    SIZE_4_2,  // 4:2 aspect ratio widget (larger)
    SIZE_3_2   // 3:2 aspect ratio widget (compact)
}

/**
 * 4:2 Widget - Larger widget with more detailed information
 */
@Composable
fun Widget4x2(
    data: WidgetAttendanceData?,
    onTap: () -> Unit = {}
) {
    val hasData = data != null && !data.isLoading
    val isLoading = data?.isLoading == true
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
            .clickable { onTap() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: Student info and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Student info
                Column {
                    Text(
                        text = data?.studentName?.takeIf { it.isNotBlank() }?.uppercase() ?: "STUDENT",
                        color = TrackerColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = data?.rollNumber?.takeIf { it.isNotBlank() } ?: "—",
                        color = TrackerColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Status indicator
                AttendanceStatusIndicator(
                    percentage = data?.overallPercentage,
                    isLoading = isLoading,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Middle section: Attendance percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TrackerColors.TextMuted,
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        val percentage = data?.overallPercentage ?: 0.0
                        val statusColor = when {
                            !hasData -> TrackerColors.TextMuted
                            percentage >= 80.0 -> TrackerColors.SafeEmerald
                            percentage >= 75.0 -> TrackerColors.WarningAmber
                            else -> TrackerColors.DangerRose
                        }
                        
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = formatPercentage(percentage),
                                color = if (hasData) TrackerColors.TextPrimary else TrackerColors.TextMuted,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "%",
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ATTENDANCE",
                            color = TrackerColors.TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                // Classes attended/conducted
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (hasData) "${data.totalAttended} / ${data.totalConducted}" else "— / —",
                        color = if (hasData) TrackerColors.TextPrimary else TrackerColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "CLASSES",
                        color = TrackerColors.TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                }
            }
            
            // Bottom section: Can Skip status and Today's attendance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Can Skip status
                Column {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TrackerColors.SurfaceElevated)
                            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val safeSkips = data?.safeSkips
                            val isSafe = safeSkips?.status?.equals("Safe", ignoreCase = true) ?: true
                            val accent = if (isSafe) TrackerColors.SafeEmerald else TrackerColors.DangerRose
                            
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (!hasData) TrackerColors.TextMuted else accent)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    !hasData -> "SYNCING"
                                    data.safeSkips?.status.equals("Safe", ignoreCase = true) -> "CAN SKIP"
                                    else -> "ATTEND"
                                },
                                color = if (!hasData) TrackerColors.TextMuted else accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            !hasData -> "Loading..."
                            data.safeSkips?.status.equals("Safe", ignoreCase = true) -> "${data.safeSkips?.periods ?: 0} periods"
                            else -> "Need ${data.safeSkips?.classesNeededToRecover ?: 0}"
                        },
                        color = TrackerColors.TextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Today's attendance indicator
                Column(horizontalAlignment = Alignment.End) {
                    val todayStatus = getTodayAttendanceStatus(data?.todayAttendance)
                    val todayColor = when (todayStatus) {
                        TodayStatus.FULL -> TrackerColors.SafeEmerald
                        TodayStatus.PARTIAL -> TrackerColors.WarningAmber
                        TodayStatus.NONE -> TrackerColors.TextMuted
                        TodayStatus.ABSENT -> TrackerColors.DangerRose
                        TodayStatus.LOADING -> TrackerColors.TextMuted
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(todayColor.copy(alpha = 0.15f))
                            .border(1.dp, todayColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = todayStatus.symbol,
                            color = todayColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "TODAY",
                        color = TrackerColors.TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}

/**
 * 3:2 Widget - Compact widget with essential information
 */
@Composable
fun Widget3x2(
    data: WidgetAttendanceData?,
    onTap: () -> Unit = {}
) {
    val hasData = data != null && !data.isLoading
    val isLoading = data?.isLoading == true
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .clickable { onTap() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: Student name (very compact)
            Text(
                text = data?.studentName?.takeIf { it.isNotBlank() }?.let {
                    if (it.length > 12) "${it.substring(0, 10)}.." else it
                }?.uppercase() ?: "STUDENT",
                color = TrackerColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Middle section: Attendance percentage with status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = TrackerColors.TextMuted,
                        strokeWidth = 1.5.dp
                    )
                } else {
                    val percentage = data?.overallPercentage ?: 0.0
                    val statusColor = when {
                        !hasData -> TrackerColors.TextMuted
                        percentage >= 80.0 -> TrackerColors.SafeEmerald
                        percentage >= 75.0 -> TrackerColors.WarningAmber
                        else -> TrackerColors.DangerRose
                    }
                    
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = formatPercentage(percentage),
                                color = if (hasData) TrackerColors.TextPrimary else TrackerColors.TextMuted,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(1.dp))
                            Text(
                                text = "%",
                                color = statusColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        
                        // Small status indicator
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(0.5.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = when {
                                    !hasData -> "SYNC"
                                    percentage >= 75.0 -> "SAFE"
                                    else -> "LOW"
                                },
                                color = statusColor,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                
                // Can Skip indicator (very compact)
                Column(horizontalAlignment = Alignment.End) {
                    val safeSkips = data?.safeSkips
                    val isSafe = safeSkips?.status?.equals("Safe", ignoreCase = true) ?: true
                    val accent = if (isSafe) TrackerColors.SafeEmerald else TrackerColors.DangerRose
                    
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.15f))
                            .border(0.5.dp, accent.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSafe) "✓" else "!",
                            color = accent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SKIP",
                        color = TrackerColors.TextMuted,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            
            // Bottom section: Roll number and today's status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data?.rollNumber?.takeIf { it.isNotBlank() } ?: "—",
                    color = TrackerColors.TextMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Today's status mini indicator
                val todayStatus = getTodayAttendanceStatus(data?.todayAttendance)
                val todayColor = when (todayStatus) {
                    TodayStatus.FULL -> TrackerColors.SafeEmerald
                    TodayStatus.PARTIAL -> TrackerColors.WarningAmber
                    TodayStatus.NONE -> TrackerColors.TextMuted
                    TodayStatus.ABSENT -> TrackerColors.DangerRose
                    TodayStatus.LOADING -> TrackerColors.TextMuted
                }
                
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(todayColor.copy(alpha = 0.15f))
                        .border(0.5.dp, todayColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = todayStatus.symbol,
                        color = todayColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Helper function to determine today's attendance status
 */
private fun getTodayAttendanceStatus(todayAttendance: List<Badge>?): TodayStatus {
    if (todayAttendance == null) return TodayStatus.LOADING
    if (todayAttendance.isEmpty()) return TodayStatus.NONE
    
    val hasPresent = todayAttendance.any { badge ->
        badge.status.uppercase().contains('P')
    }
    val hasAbsent = todayAttendance.any { badge ->
        badge.status.uppercase().contains('A')
    }
    
    return when {
        hasPresent && !hasAbsent -> TodayStatus.FULL
        hasPresent && hasAbsent -> TodayStatus.PARTIAL
        !hasPresent && hasAbsent -> TodayStatus.ABSENT
        else -> TodayStatus.NONE
    }
}

/**
 * Today's attendance status enum
 */
private enum class TodayStatus(val symbol: String) {
    FULL("✓"),      // All present
    PARTIAL("~"),   // Mixed present/absent
    NONE("—"),      // No attendance recorded
    ABSENT("✗"),    // All absent
    LOADING("↻")    // Loading/syncing
}

/**
 * Attendance status indicator (circular indicator)
 */
@Composable
private fun AttendanceStatusIndicator(
    percentage: Double?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        isLoading -> TrackerColors.TextMuted
        percentage == null -> TrackerColors.TextMuted
        percentage >= 80.0 -> TrackerColors.SafeEmerald
        percentage >= 75.0 -> TrackerColors.WarningAmber
        else -> TrackerColors.DangerRose
    }
    
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(statusColor.copy(alpha = 0.15f))
            .border(1.dp, statusColor.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = statusColor,
                strokeWidth = 1.5.dp
            )
        } else {
            Box(
                modifier = Modifier.size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }
    }
}



/**
 * Preview composable for testing widgets in Android Studio preview
 */
@Composable
fun WidgetPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(TrackerColors.PureBlack),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Widget Previews",
            color = TrackerColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
        
        // 4:2 Widget Preview
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "4:2 Widget (Large)",
                color = TrackerColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Widget4x2(
                    data = WidgetAttendanceData(
                        studentName = "John Doe",
                        rollNumber = "21B01A1234",
                        overallPercentage = 85.5,
                        safeSkips = SkipsInfo(
                            periods = 3,
                            days = 2,
                            remainingPeriods = 3,
                            projectedPercentage = 85.0,
                            status = "Safe",
                            classesNeededToRecover = 0
                        ),
                        todayAttendance = listOf(
                            Badge("Math", "P", 1, 0),
                            Badge("Physics", "P", 1, 0),
                            Badge("Chemistry", "A", 0, 1)
                        ),
                        totalAttended = 45,
                        totalConducted = 52,
                        isLoading = false
                    ),
                    onTap = {}
                )
            }
        }
        
        // 3:2 Widget Preview
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "3:2 Widget (Compact)",
                color = TrackerColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // First 3:2 widget
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Widget3x2(
                        data = WidgetAttendanceData(
                            studentName = "John Doe",
                            rollNumber = "21B01A1234",
                            overallPercentage = 85.5,
                            safeSkips = SkipsInfo(
                                periods = 3,
                                days = 2,
                                remainingPeriods = 3,
                                projectedPercentage = 85.0,
                                status = "Safe",
                                classesNeededToRecover = 0
                            ),
                            todayAttendance = listOf(
                                Badge("Math", "P", 1, 0),
                                Badge("Physics", "P", 1, 0)
                            ),
                            totalAttended = 45,
                            totalConducted = 52,
                            isLoading = false
                        ),
                        onTap = {}
                    )
                }
                
                // Second 3:2 widget (low attendance)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Widget3x2(
                        data = WidgetAttendanceData(
                            studentName = "Jane Smith",
                            rollNumber = "21B01A5678",
                            overallPercentage = 65.2,
                            safeSkips = SkipsInfo(
                                periods = 0,
                                days = 0,
                                remainingPeriods = 0,
                                projectedPercentage = 65.2,
                                status = "Critical",
                                classesNeededToRecover = 5
                            ),
                            todayAttendance = listOf(
                                Badge("Math", "A", 0, 1),
                                Badge("Physics", "A", 0, 1)
                            ),
                            totalAttended = 32,
                            totalConducted = 49,
                            isLoading = false
                        ),
                        onTap = {}
                    )
                }
            }
        }
        
        // Loading state preview
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Loading States",
                color = TrackerColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 4:2 loading
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(240.dp)
                        .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Widget4x2(
                        data = WidgetAttendanceData(
                            studentName = "",
                            rollNumber = "",
                            overallPercentage = 0.0,
                            safeSkips = null,
                            todayAttendance = null,
                            totalAttended = 0,
                            totalConducted = 0,
                            isLoading = true
                        ),
                        onTap = {}
                    )
                }
                
                // 3:2 loading
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Widget3x2(
                        data = WidgetAttendanceData(
                            studentName = "",
                            rollNumber = "",
                            overallPercentage = 0.0,
                            safeSkips = null,
                            todayAttendance = null,
                            totalAttended = 0,
                            totalConducted = 0,
                            isLoading = true
                        ),
                        onTap = {}
                    )
                }
            }
        }
    }
}