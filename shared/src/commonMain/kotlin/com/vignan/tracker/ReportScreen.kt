package com.vignan.tracker

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Range selector modes, top to bottom on the screen. */
private enum class ReportMode(val label: String) {
    PER_MONTH("MONTH"),
    TILL_NOW("TILL NOW"),
    FROM_TO("FROM - TO")
}

/** Month choices for the MONTH dropdown: value = "MM" (as the portal expects). */
private val MONTH_OPTIONS: List<Pair<String, String>> = listOf(
    "01" to "January",
    "02" to "February",
    "03" to "March",
    "04" to "April",
    "05" to "May",
    "06" to "June",
    "07" to "July",
    "08" to "August",
    "09" to "September",
    "10" to "October",
    "11" to "November",
    "12" to "December"
)

/**
 * Attendance screen (portal ATTENDANCE REPORT / ShowAttendance):
 * per-month breakdown, semester-to-date, or an arbitrary from-to range.
 */
@Composable
fun ReportScreen() {
    val repository = remember { AttendanceRepository() }
    val scope = rememberCoroutineScope()

    val now = remember { java.util.Calendar.getInstance() }
    val monthOptions = remember { MONTH_OPTIONS }
    // Current year first, then a few previous ones (reports are historical).
    val yearOptions = remember {
        val current = now.get(java.util.Calendar.YEAR)
        (current downTo current - 4).map { it.toString() }
    }

    var selectedMode by remember { mutableStateOf(ReportMode.PER_MONTH) }
    var month by remember { mutableStateOf((now.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')) }
    var year by remember { mutableStateOf(now.get(java.util.Calendar.YEAR).toString()) }
    var fromDate by remember { mutableStateOf("") } // DD/MM/YYYY
    var toDate by remember { mutableStateOf("") }   // DD/MM/YYYY
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    var isReportLoading by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<AttendanceReportResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var requestedRange by remember { mutableStateOf("semester to date") }

    fun load(from: String?, to: String?, rangeLabel: String) {
        isReportLoading = true
        error = null
        requestedRange = rangeLabel
        scope.launch {
            val result = repository.fetchAttendanceReport(fromDate = from, toDate = to)
            result.fold(
                onSuccess = { report = it },
                onFailure = { error = it.message?.ifBlank { null } ?: "Could not load the attendance." }
            )
            isReportLoading = false
        }
    }

    fun loadForMode() {
        when (selectedMode) {
            ReportMode.PER_MONTH -> {
                // A whole month is requested by passing only its first day — the
                // backend fills in the end of the range, so no toDate is sent.
                load("01/$month/$year", null, "${monthName(month)} $year")
            }
            // Semester to date: neither date is needed, just omit both.
            ReportMode.TILL_NOW -> load(null, null, "semester to date")
            ReportMode.FROM_TO -> {
                if (fromDate.isBlank() || toDate.isBlank()) {
                    error = "Pick both the from and to dates."
                    report = null
                    return
                }
                load(fromDate, toDate, "$fromDate → $toDate")
            }
        }
    }

    fun selectMode(mode: ReportMode) {
        if (mode == selectedMode) return
        selectedMode = mode
        error = null
        when (mode) {
            // Till now needs no input, so load it straight away.
            ReportMode.TILL_NOW -> loadForMode()
            // Monthly is fully driven by the dropdowns — load the current selection.
            ReportMode.PER_MONTH -> loadForMode()
            // From-to waits for the user to pick dates.
            ReportMode.FROM_TO -> report = null
        }
    }

    // Initial load: the current month is preselected.
    LaunchedEffect(Unit) { loadForMode() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReportHeader()

        ReportModeSelector(selected = selectedMode, onSelect = { selectMode(it) })

        // ── Range panel ──────────────────────────────────────────────────
        val pendingRange = when (selectedMode) {
            ReportMode.PER_MONTH -> "01/$month/$year"
            ReportMode.TILL_NOW -> "SEMESTER → TODAY"
            ReportMode.FROM_TO -> if (fromDate.isBlank() || toDate.isBlank()) "PICK A RANGE" else "$fromDate  →  $toDate"
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RANGE",
                    color = TrackerColors.TextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = pendingRange,
                    color = TrackerColors.TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (selectedMode) {
                ReportMode.PER_MONTH -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportDropdownField(
                            label = "MONTH",
                            value = month,
                            displayValue = monthName(month),
                            options = monthOptions,
                            onSelect = { month = it },
                            modifier = Modifier.weight(1.15f)
                        )
                        ReportDropdownField(
                            label = "YEAR",
                            value = year,
                            displayValue = year,
                            options = yearOptions.map { it to it },
                            onSelect = { year = it },
                            modifier = Modifier.weight(0.85f)
                        )
                    }
                    ReportLoadButton(
                        enabled = !isReportLoading,
                        onClick = { loadForMode() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ReportMode.TILL_NOW -> {
                    Text(
                        text = "Every class from the start of the semester through today.",
                        color = TrackerColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ReportLoadButton(
                        enabled = !isReportLoading,
                        onClick = { loadForMode() },
                        label = "Reload",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ReportMode.FROM_TO -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportDatePickerField(
                            label = "FROM",
                            value = fromDate,
                            onClick = { showFromPicker = true },
                            modifier = Modifier.weight(1f)
                        )
                        ReportDatePickerField(
                            label = "TO",
                            value = toDate,
                            onClick = { showToPicker = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ReportLoadButton(
                        enabled = !isReportLoading,
                        onClick = { loadForMode() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── Result ───────────────────────────────────────────────────────
        when {
            isReportLoading -> ReportSkeleton()
            error != null -> ReportErrorCard(error ?: "", onRetry = { loadForMode() })
            report != null && report!!.subjects.isEmpty() -> ReportEmptyCard(requestedRange)
            report != null -> ReportResultCard(report!!, requestedRange)
        }
    }

    // ── Date pickers (FROM-TO) ───────────────────────────────────────────
    if (showFromPicker) {
        ReportDatePickerDialog(
            initialMillis = parseDdMmYyyyMillis(fromDate),
            onDismiss = { showFromPicker = false },
            onConfirm = { fromDate = it }
        )
    }
    if (showToPicker) {
        ReportDatePickerDialog(
            initialMillis = parseDdMmYyyyMillis(toDate),
            onDismiss = { showToPicker = false },
            onConfirm = { toDate = it }
        )
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun ReportHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
    ) {
        Text(
            text = "ATTENDANCE",
            color = TrackerColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Subject-wise breakdown",
            color = TrackerColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
    }
}

// ---------------------------------------------------------------- controls

/** Segmented control: one hairline container with a filled highlight for the active mode. */
@Composable
private fun ReportModeSelector(selected: ReportMode, onSelect: (ReportMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ReportMode.entries.forEach { mode ->
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

/** "01" → "January"; falls back to the raw value for anything unexpected. */
private fun monthName(mm: String): String =
    MONTH_OPTIONS.firstOrNull { it.first == mm }?.second ?: mm

/** DatePickerState millis are UTC midnight — format in UTC so the day never shifts. */
private fun formatDdMmYyyy(utcMillis: Long): String {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMillis
    }
    val dd = cal.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    val mm = (cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
    val yyyy = cal.get(java.util.Calendar.YEAR).toString()
    return "$dd/$mm/$yyyy"
}

/** Inverse of [formatDdMmYyyy]; null when the field is empty or malformed. */
private fun parseDdMmYyyyMillis(raw: String): Long? {
    val parts = raw.split("/")
    if (parts.size != 3) return null
    val dd = parts[0].toIntOrNull() ?: return null
    val mm = parts[1].toIntOrNull() ?: return null
    val yyyy = parts[2].toIntOrNull() ?: return null
    if (dd !in 1..31 || mm !in 1..12) return null
    return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(yyyy, mm - 1, dd)
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDatePickerDialog(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(formatDdMmYyyy(it)) }
                    onDismiss()
                }
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/** Read-only date field that opens the picker instead of the keyboard. */
@Composable
private fun ReportDatePickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TrackerColors.SurfaceInput)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            FieldLabel(label)
            Text(
                text = value.ifBlank { "DD/MM/YYYY" },
                color = if (value.isBlank()) TrackerColors.TextMuted else TrackerColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
        Text(
            text = "▾",
            color = TrackerColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** Tappable field that opens a dropdown of [options] (value to label). */
@Composable
private fun ReportDropdownField(
    label: String,
    value: String,
    displayValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TrackerColors.SurfaceInput)
                .border(
                    1.dp,
                    if (expanded) TrackerColors.BorderFocused else TrackerColors.HairlineBorder,
                    RoundedCornerShape(8.dp)
                )
                .clickable { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel(label)
                Text(
                    text = displayValue,
                    color = TrackerColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "▾",
                color = TrackerColors.TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = TrackerColors.SurfaceElevated
        ) {
            options.forEach { (optValue, optLabel) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optLabel,
                            color = if (optValue == value) TrackerColors.PrimaryWhite else TrackerColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (optValue == value) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    onClick = {
                        onSelect(optValue)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = TrackerColors.TextMuted,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun ReportLoadButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Load attendance"
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) TrackerColors.PrimaryWhite else TrackerColors.SurfaceElevated)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) TrackerColors.PureBlack else TrackerColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.6.sp
        )
    }
}

// ---------------------------------------------------------------- results

/** Status thresholds shared by the hero and the subject rows. */
private fun statusColor(percentage: Double): Color = when {
    percentage >= 80.0 -> TrackerColors.SafeEmerald
    percentage >= 75.0 -> TrackerColors.WarningAmber
    else -> TrackerColors.DangerRose
}

private fun statusLabel(percentage: Double): String = when {
    percentage >= 80.0 -> "SAFE"
    percentage >= 75.0 -> "WATCH"
    else -> "LOW"
}

/** Thin rounded progress bar, animated into place. */
@Composable
private fun ReportProgressBar(fraction: Double, color: Color, height: Int = 6) {
    val progress by animateFloatAsState(
        targetValue = fraction.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = tween(600),
        label = "reportProgress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CircleShape)
            .background(TrackerColors.HairlineBorder)
    ) {
        // fillMaxWidth(fraction) needs a positive fraction, so a 0% bar (or a
        // still-animating 0) just leaves the empty track showing.
        if (progress > 0.001f) {
            Box(
                modifier = Modifier
                    .height(height.dp)
                    .fillMaxWidth(progress)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun ReportResultCard(report: AttendanceReportResponse, rangeLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReportSummaryCard(report, rangeLabel)
        ReportSubjectList(report)
    }
}

@Composable
private fun ReportSummaryCard(report: AttendanceReportResponse, rangeLabel: String) {
    val aggregate = report.aggregate
    val pctColor = statusColor(aggregate.percentage)
    val missed = (aggregate.held - aggregate.attended).coerceAtLeast(0)

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OVERALL",
                    color = TrackerColors.TextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = rangeLabel,
                    color = TrackerColors.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ReportStatusPill(percentage = aggregate.percentage)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatPercentage(aggregate.percentage),
                color = pctColor,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "attendance",
                color = TrackerColors.TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        ReportProgressBar(fraction = aggregate.percentage / 100.0, color = pctColor)

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ReportStat(label = "HELD", value = aggregate.held.toString(), modifier = Modifier.weight(1f))
            ReportStat(label = "ATTENDED", value = aggregate.attended.toString(), modifier = Modifier.weight(1f))
            ReportStat(label = "MISSED", value = missed.toString(), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReportStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = TrackerColors.TextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = TrackerColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ReportStatusPill(percentage: Double) {
    val color = statusColor(percentage)
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = statusLabel(percentage),
            color = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun ReportSubjectList(report: AttendanceReportResponse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SUBJECTS",
                color = TrackerColors.TextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
            Text(
                text = report.subjects.size.toString().padStart(2, '0'),
                color = TrackerColors.TextSubtle,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        report.subjects.forEachIndexed { index, subject ->
            val color = statusColor(subject.percentage)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color)
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
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${subject.attended}/${subject.held}",
                        color = TrackerColors.TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formatPercentage(subject.percentage),
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                ReportProgressBar(fraction = subject.percentage / 100.0, color = color, height = 4)
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

// ---------------------------------------------------------------- states

/** Placeholder card while the attendance request is in flight. */
@Composable
private fun ReportSkeleton() {
    val transition = rememberInfiniteTransition(label = "reportSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "reportSkeletonAlpha"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBar(widthFraction = 0.30f, barHeight = 10, alpha = alpha)
            SkeletonBar(widthFraction = 0.55f, barHeight = 30, alpha = alpha)
            SkeletonBar(widthFraction = 1f, barHeight = 6, alpha = alpha)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    SkeletonBar(
                        widthFraction = 1f,
                        barHeight = 16,
                        alpha = alpha,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            for (i in 0 until 4) {
                SkeletonBar(widthFraction = 0.85f - i * 0.12f, barHeight = 12, alpha = alpha)
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    widthFraction: Float,
    barHeight: Int,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(barHeight.dp)
            .clip(CircleShape)
            .background(TrackerColors.SurfaceElevated.copy(alpha = alpha))
    )
}

@Composable
private fun ReportErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
        ReportLoadButton(
            enabled = true,
            onClick = onRetry,
            label = "Try again",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReportEmptyCard(rangeLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "NO CLASSES RECORDED",
            color = TrackerColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Nothing logged for $rangeLabel. Try a different range.",
            color = TrackerColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
