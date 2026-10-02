package com.vignan.tracker

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Range selector modes, top to bottom on the screen. */
private enum class ReportMode(val label: String) {
    PER_MONTH("PER MONTH"),
    TILL_NOW("TILL NOW"),
    FROM_TO("FROM - TO")
}

/**
 * Attendance report screen (portal ATTENDANCE REPORT / ShowAttendance):
 * per-month breakdown, semester-to-date, or an arbitrary from-to range.
 */
@Composable
fun ReportScreen() {
    val repository = remember { AttendanceRepository() }
    val scope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(ReportMode.PER_MONTH) }
    var month by remember { mutableStateOf("") }   // MM
    var year by remember { mutableStateOf("") }    // YYYY
    var fromDate by remember { mutableStateOf("") } // DD/MM/YYYY
    var toDate by remember { mutableStateOf("") }   // DD/MM/YYYY

    var isReportLoading by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<AttendanceReportResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var requestedRange by remember { mutableStateOf("semester to date") }

    fun isValidDate(d: String): Boolean {
        val p = d.split("/")
        if (p.size != 3) return false
        val dd = p[0].toIntOrNull() ?: return false
        val mm = p[1].toIntOrNull() ?: return false
        val yyyy = p[2].toIntOrNull() ?: return false
        return dd in 1..31 && mm in 1..12 && yyyy in 2000..2100
    }

    fun load(from: String?, to: String?, rangeLabel: String) {
        isReportLoading = true
        error = null
        requestedRange = rangeLabel
        scope.launch {
            val result = repository.fetchAttendanceReport(fromDate = from, toDate = to)
            result.fold(
                onSuccess = { report = it },
                onFailure = { error = it.message?.ifBlank { null } ?: "Could not load the report." }
            )
            isReportLoading = false
        }
    }

    fun loadForMode() {
        when (selectedMode) {
            ReportMode.PER_MONTH -> {
                val mm = month.padStart(2, '0')
                val yyyy = year.padStart(4, '0')
                if (mm.toIntOrNull() !in 1..12 || year.length != 4) {
                    error = "Enter a valid month (01-12) and year."
                    report = null
                    return
                }
                load("$mm/01/$yyyy", "$mm/31/$yyyy", "month $mm/$yyyy")
            }
            ReportMode.TILL_NOW -> load(null, null, "semester to date")
            ReportMode.FROM_TO -> {
                if (!isValidDate(fromDate) || !isValidDate(toDate)) {
                    error = "Enter both dates as DD/MM/YYYY."
                    report = null
                    return
                }
                load(fromDate, toDate, "$fromDate → $toDate")
            }
        }
    }

    // Initial load: default to the current month once credentials are available.
    LaunchedEffect(Unit) {
        val now = java.util.Calendar.getInstance()
        month = ((now.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0'))
        year = now.get(java.util.Calendar.YEAR).toString()
        loadForMode()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Mode selector ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReportMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) TrackerColors.PrimaryWhite else TrackerColors.SurfaceDark)
                        .border(
                            1.dp,
                            if (selected) TrackerColors.PrimaryWhite else TrackerColors.HairlineBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedMode = mode }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) TrackerColors.PureBlack else TrackerColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // ── Inputs per mode ──────────────────────────────────────────────
        when (selectedMode) {
            ReportMode.PER_MONTH -> {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportDateField(
                        value = month,
                        onValueChange = { month = it.take(2).filter(Char::isDigit) },
                        label = "MM",
                        modifier = Modifier.width(90.dp)
                    )
                    ReportDateField(
                        value = year,
                        onValueChange = { year = it.take(4).filter(Char::isDigit) },
                        label = "YYYY",
                        modifier = Modifier.width(110.dp)
                    )
                    FetchReportButton(
                        enabled = !isReportLoading,
                        onClick = { loadForMode() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            ReportMode.TILL_NOW -> {
                // No inputs needed; the initial load already covers it.
            }
            ReportMode.FROM_TO -> {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportDateField(
                            value = fromDate,
                            onValueChange = { fromDate = it.take(10) },
                            label = "DD/MM/YYYY",
                            modifier = Modifier.weight(1f)
                        )
                        ReportDateField(
                            value = toDate,
                            onValueChange = { toDate = it.take(10) },
                            label = "DD/MM/YYYY",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    FetchReportButton(
                        enabled = !isReportLoading,
                        onClick = { loadForMode() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Result ───────────────────────────────────────────────────────
        when {
            isReportLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = TrackerColors.TextMuted,
                    strokeWidth = 2.dp
                )
            }
            error != null -> ReportErrorCard(error ?: "")
            report != null && report!!.subjects.isEmpty() -> ReportEmptyCard(requestedRange)
            report != null -> ReportResultCard(report!!, requestedRange)
        }
    }
}

// ---------------------------------------------------------------- controls

@Composable
private fun ReportDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = androidx.compose.ui.text.TextStyle(
            color = TrackerColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        ),
        label = {
            Text(
                text = label,
                color = TrackerColors.TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = TrackerColors.SurfaceInput,
            unfocusedContainerColor = TrackerColors.SurfaceInput,
            cursorColor = TrackerColors.TextPrimary,
            focusedIndicatorColor = TrackerColors.BorderFocused,
            unfocusedIndicatorColor = TrackerColors.HairlineBorder,
            focusedLabelColor = TrackerColors.TextSecondary,
            unfocusedLabelColor = TrackerColors.TextMuted
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    )
}

@Composable
private fun FetchReportButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) TrackerColors.PrimaryWhite else TrackerColors.SurfaceElevated)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Load report",
            color = if (enabled) TrackerColors.PureBlack else TrackerColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif
        )
    }
}

// ---------------------------------------------------------------- results

@Composable
private fun ReportResultCard(report: AttendanceReportResponse, rangeLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Aggregate header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ATTENDANCE REPORT",
                        color = TrackerColors.TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = rangeLabel,
                        color = TrackerColors.TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val pctColor = when {
                    report.aggregate.percentage >= 80.0 -> TrackerColors.SafeEmerald
                    report.aggregate.percentage >= 75.0 -> TrackerColors.WarningAmber
                    else -> TrackerColors.DangerRose
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatPercentage(report.aggregate.percentage),
                        color = pctColor,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${report.aggregate.attended} / ${report.aggregate.held} CLASSES",
                        color = TrackerColors.TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        // Per-subject rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
        ) {
            report.subjects.forEachIndexed { index, subject ->
                val pctColor = when {
                    subject.percentage >= 80.0 -> TrackerColors.SafeEmerald
                    subject.percentage >= 75.0 -> TrackerColors.WarningAmber
                    else -> TrackerColors.DangerRose
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(pctColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = subject.subject.uppercase(),
                        color = TrackerColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${subject.attended}/${subject.held}",
                        color = TrackerColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formatPercentage(subject.percentage),
                        color = pctColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (index != report.subjects.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .height(1.dp)
                            .background(TrackerColors.HairlineBorder)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportErrorCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.DangerRose.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(TrackerColors.DangerRose)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "REPORT UNAVAILABLE",
                    color = TrackerColors.DangerRose,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                color = TrackerColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ReportEmptyCard(rangeLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "No attendance recorded for $rangeLabel.",
            color = TrackerColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
