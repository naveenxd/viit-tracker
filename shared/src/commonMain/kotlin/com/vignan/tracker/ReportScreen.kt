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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

/** Month choices for the PER MONTH dropdown: value = "MM" (as the portal expects). */
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
 * Attendance report screen (portal ATTENDANCE REPORT / ShowAttendance):
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
                onFailure = { error = it.message?.ifBlank { null } ?: "Could not load the report." }
            )
            isReportLoading = false
        }
    }

    fun loadForMode() {
        when (selectedMode) {
            ReportMode.PER_MONTH -> {
                // Portal wants the range as DD/MM/YYYY. A whole month is requested by
                // passing only the first day — the backend defaults the end of range.
                val mm = month.padStart(2, '0')
                val yyyy = year.padStart(4, '0')
                load("01/$mm/$yyyy", null, "${monthName(mm)} $yyyy")
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
                        .clickable { selectMode(mode) }
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
                    FetchReportButton(
                        enabled = !isReportLoading,
                        onClick = { loadForMode() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            ReportMode.TILL_NOW -> {
                // No inputs needed; selecting this mode loads it automatically.
            }
            ReportMode.FROM_TO -> {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

// ---------------------------------------------------------------- controls

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
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TrackerColors.SurfaceInput)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TrackerColors.TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
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
                .height(56.dp)
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
                Text(
                    text = label,
                    color = TrackerColors.TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
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
