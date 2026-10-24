package com.vignan.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Which mark the calendar is editing.
 *
 * They are two readings of a day, not two labels for one thing: a leave is a
 * class day the user will miss (held goes up, attended does not), a holiday is
 * a day the college does not run at all (nothing goes up), which is why the
 * holiday case walks the same path a Sunday does.
 */
internal enum class MarkKind(val title: String, val explanation: String) {
    HOLIDAY(
        title = "Mark holidays",
        explanation = "Days the college is shut. Nothing is added to either tally — the same as a Sunday."
    ),
    LEAVE(
        title = "Mark leaves",
        explanation = "Days you plan to skip. Their classes count as held but not attended."
    )
}

/**
 * The two calendar entry points, sitting between the refresh button and the
 * projection card.
 *
 * They are part of the projection feature but live outside its card: two more
 * controls inside an already busy header would have crowded out the forecast
 * they exist to shape.
 */
@Composable
internal fun ProjectionMarkButtons(onOpen: (MarkKind) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MarkKind.entries.forEach { kind ->
            val count = when (kind) {
                MarkKind.HOLIDAY -> ProjectionMarks.holidayDates.size
                MarkKind.LEAVE -> ProjectionMarks.absentDates.size
            }
            MarkButton(
                label = kind.title,
                count = count,
                accent = kind.accent(),
                onClick = { onOpen(kind) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** [MarkKind] colour: holidays read neutral, leaves read as the app's warning hue. */
@Composable
internal fun MarkKind.accent(): Color = when (this) {
    MarkKind.HOLIDAY -> TrackerColors.TextPrimary
    MarkKind.LEAVE -> TrackerColors.DangerRose
}

@Composable
private fun MarkButton(
    label: String,
    count: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val marked = count > 0
    val tint = if (marked) accent else TrackerColors.TextSecondary

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TrackerColors.SurfaceDark)
            .border(
                1.dp,
                if (marked) accent.copy(alpha = 0.35f) else TrackerColors.HairlineBorder,
                RoundedCornerShape(8.dp)
            )
            .pressClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalendarGlyph(tint = tint)

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (marked) "$label  ·  $count" else label,
                color = tint,
                fontSize = 12.sp,
                fontWeight = if (marked) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
        }
    }
}

/**
 * Calendar mark for the two buttons: frame, header band and the two binder
 * rings, drawn at the same 1.4dp weight as every other glyph in the app so the
 * button row reads as one family with the tab bar and header icons.
 */
@Composable
private fun CalendarGlyph(modifier: Modifier = Modifier, tint: Color = TrackerColors.TextSecondary) {
    Canvas(modifier = modifier.size(14.dp)) {
        val sw = 1.4.dp.toPx()
        val corner = 2.dp.toPx()
        val inset = sw / 2f
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawRoundRect(
            color = tint,
            topLeft = Offset(inset, inset),
            size = Size(w - sw, h - sw),
            cornerRadius = CornerRadius(corner, corner),
            style = stroke
        )
        drawLine(tint, Offset(inset, h * 0.38f), Offset(w - inset, h * 0.38f), sw * 0.9f)
        drawLine(tint, Offset(w * 0.32f, 0f), Offset(w * 0.32f, h * 0.20f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.68f, 0f), Offset(w * 0.68f, h * 0.20f), sw, StrokeCap.Round)
    }
}

/**
 * Multi-date calendar for one [MarkKind].
 *
 * The month grid starts pre-selected with the marks already set for that kind,
 * so the sheet reads as an editor of the whole set: deselect a date and Apply
 * to drop it, or pick several and Apply to add them. Apply with nothing
 * selected clears that kind outright, which is why the button renames itself.
 */
@Composable
internal fun ProjectionMarkSheet(kind: MarkKind, onDismiss: () -> Unit) {
    val accent = kind.accent()
    val current = when (kind) {
        MarkKind.HOLIDAY -> ProjectionMarks.holidayDates
        MarkKind.LEAVE -> ProjectionMarks.absentDates
    }

    var draft by remember(kind) { mutableStateOf(current) }
    var month by remember { mutableStateOf(firstOfMonth(java.util.Calendar.getInstance().timeInMillis)) }
    // Which way the grid should travel when the month changes: set by the arrows
    // so the incoming month slides in from the side it was paged towards.
    var monthForward by remember { mutableStateOf(true) }

    val today = remember { java.util.Calendar.getInstance().timeInMillis }
    val thisMonth = remember(today) { firstOfMonth(today) }

    TrackerSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 22.dp)
        ) {
            Text(
                text = kind.title,
                color = TrackerColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = kind.explanation,
                color = TrackerColors.TextSubtle,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(12.dp))

            MonthNavigator(
                monthMillis = month,
                canGoBack = month > thisMonth,
                onPrevious = {
                    monthForward = false
                    month = shiftMonth(month, -1)
                },
                onNext = {
                    monthForward = true
                    month = shiftMonth(month, 1)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            WeekdayHeader()

            Spacer(modifier = Modifier.height(2.dp))

            AnimatedContent(
                targetState = month,
                transitionSpec = { horizontalSwap(forward = monthForward, fraction = 0.10f) },
                label = "monthGrid"
            ) { visibleMonth ->
                MonthGrid(
                    monthMillis = visibleMonth,
                    todayMillis = today,
                    selected = draft,
                    accent = accent,
                    onToggle = { key ->
                        draft = if (key in draft) draft - key else draft + key
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            ApplyButton(
                selectedCount = draft.size,
                onClick = {
                    when (kind) {
                        MarkKind.HOLIDAY -> ProjectionMarks.setHolidays(draft)
                        MarkKind.LEAVE -> ProjectionMarks.setAbsent(draft)
                    }
                    onDismiss()
                }
            )
        }
    }
}

/**
 * The sheet shell every sheet in the app wears: same container, same handle, a
 * scrim dark enough to read over the near-black page, and a blurred backdrop
 * where the platform can do it. Opening at full height too, since a sheet that
 * stops halfway only hides its own button below a scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    SheetBackdropBlur()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TrackerColors.SurfaceDark,
        contentColor = TrackerColors.TextPrimary,
        scrimColor = SheetScrim,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { SheetDragHandle() },
        content = content
    )
}

@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = 9.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(TrackerColors.HairlineBorderLight)
    )
}

@Composable
private fun MonthNavigator(
    monthMillis: Long,
    canGoBack: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthArrow(pointsLeft = true, enabled = canGoBack, onClick = onPrevious)

        Text(
            text = monthLabel(monthMillis),
            color = TrackerColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        MonthArrow(pointsLeft = false, enabled = true, onClick = onNext)
    }
}

/**
 * Month paging arrow.
 *
 * Drawn rather than typeset: the "‹" and "›" glyphs come out a different size,
 * weight and optical centre in every font, which is why the two arrows never
 * quite matched each other or the circles around them. Two strokes at the app's
 * glyph weight are the same shape in every theme and on every device.
 */
@Composable
private fun MonthArrow(pointsLeft: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) TrackerColors.TextSecondary else TrackerColors.TextSubtle.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .border(
                1.dp,
                if (enabled) TrackerColors.HairlineBorderLight else TrackerColors.HairlineBorder,
                CircleShape
            )
            .pressClickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            val sw = 1.5.dp.toPx()
            val w = size.width
            val h = size.height
            // The tip leads towards the month it pages to; the tails sit behind
            // it, so both arrows lean the same amount off centre.
            val tip = if (pointsLeft) w * 0.34f else w * 0.66f
            val tail = if (pointsLeft) w * 0.64f else w * 0.36f

            drawLine(tint, Offset(tail, h * 0.20f), Offset(tip, h * 0.5f), sw, StrokeCap.Round)
            drawLine(tint, Offset(tip, h * 0.5f), Offset(tail, h * 0.80f), sw, StrokeCap.Round)
        }
    }
}

/**
 * The week rows are counted per month rather than fixed at six: October 2026
 * needs four leading blanks and five rows, and a reserved sixth would leave an
 * empty strip between the last week and Apply. The cost is a sheet that is one
 * row taller in the months that need it.
 */
@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
            Text(
                text = day,
                color = TrackerColors.TextSubtle,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MonthGrid(
    monthMillis: Long,
    todayMillis: Long,
    selected: Set<String>,
    accent: Color,
    onToggle: (String) -> Unit
) {
    val cal = remember(monthMillis) {
        java.util.Calendar.getInstance().apply {
            timeInMillis = monthMillis
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }
    }
    // 0 = Sunday, matching the weekday header above (and the projection's week map).
    val leadingBlanks = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

    // Each day's instant, walked with Calendar.add rather than a fixed 24h stride:
    // a DST shift would otherwise land an hour early and mislabel the key.
    val dayMillisList = remember(monthMillis) {
        val first = java.util.Calendar.getInstance().apply {
            timeInMillis = monthMillis
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        (1..daysInMonth).map { day ->
            (first.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_MONTH, day - 1)
            }.timeInMillis
        }
    }
    val todayKey = remember(todayMillis) { projectionDateKey(todayMillis) }
    val weekCount = (leadingBlanks + daysInMonth + 6) / 7

    Column {
        repeat(weekCount) { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val dayOfMonth = week * 7 + column - leadingBlanks + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val dayMillis = dayMillisList[dayOfMonth - 1]
                        val key = projectionDateKey(dayMillis)
                        val isSunday = column == 0
                        val isPast = dayMillis < startOfDay(todayMillis)

                        DayCell(
                            dayOfMonth = dayOfMonth,
                            // Sundays are already off and the past cannot be re-planned,
                            // so neither is selectable.
                            enabled = !isSunday && !isPast,
                            selected = key in selected,
                            isToday = key == todayKey,
                            accent = accent,
                            onClick = { onToggle(key) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).height(CELL_HEIGHT))
                    }
                }
            }
        }
    }
}

private val CELL_HEIGHT = 36.dp

@Composable
private fun DayCell(
    dayOfMonth: Int,
    enabled: Boolean,
    selected: Boolean,
    isToday: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .padding(2.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                1.dp,
                when {
                    selected -> accent.copy(alpha = 0.45f)
                    isToday -> TrackerColors.HairlineBorderLight
                    else -> Color.Transparent
                },
                RoundedCornerShape(7.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$dayOfMonth",
            color = when {
                selected -> accent
                !enabled -> TrackerColors.TextSubtle.copy(alpha = 0.4f)
                isToday -> TrackerColors.TextPrimary
                else -> TrackerColors.TextSecondary
            },
            fontSize = 12.sp,
            fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ApplyButton(selectedCount: Int, onClick: () -> Unit) {
    val empty = selectedCount == 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (empty) TrackerColors.SurfaceElevated else TrackerColors.PrimaryWhite)
            .pressClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                empty -> "CLEAR MARKS"
                selectedCount == 1 -> "APPLY  ·  1 DAY"
                else -> "APPLY  ·  $selectedCount DAYS"
            },
            color = if (empty) TrackerColors.TextMuted else TrackerColors.PureBlack,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
    }
}

// ---------------------------------------------------------------- help sheet

/**
 * Usage sheet behind the header's "i": what the forecast is, in one line per
 * idea. Kept deliberately terse — it explains two gestures and a range choice,
 * and nothing else needs saying.
 */
@Composable
internal fun ProjectionHelpSheet(onDismiss: () -> Unit) {
    TrackerSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                text = "Attendance projection",
                color = TrackerColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Where your attendance lands each day if you attend everything " +
                    "still scheduled. The buffer column is what you can still skip.",
                color = TrackerColors.TextSubtle,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(16.dp))

            HelpRow(
                title = "10D · 30D · 60D",
                body = "How far ahead to forecast. Sundays always stay off."
            )
            HelpRow(
                title = "Swipe left · bunk",
                body = "The day's classes count as held, not attended. Swipe again to restore."
            )
            HelpRow(
                title = "Swipe right · holiday",
                body = "The day is shut, like a Sunday: nothing is added at all."
            )
            HelpRow(
                title = "Mark leaves · mark holidays",
                body = "Pick dates in a calendar. Leaves bunk them, holidays close them."
            )
            HelpRow(
                title = "Select period · today",
                body = "Pick any period the portal has not booked yet. The forecast updates immediately."
            )
        }
    }
}

@Composable
private fun HelpRow(title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(TrackerColors.HairlineBorderLight)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                color = TrackerColors.TextMuted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = body,
                color = TrackerColors.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

// ---------------------------------------------------------------- calendar maths

/** First millisecond of the month [millis] falls in. */
private fun firstOfMonth(millis: Long): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = millis
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

/** [millis] moved whole months, always landing on the first of the target month. */
private fun shiftMonth(millis: Long, by: Int): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = millis
        add(java.util.Calendar.MONTH, by)
    }.timeInMillis

/** Midnight today, the floor below which a date cannot be planned. */
private fun startOfDay(millis: Long): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = millis
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun monthLabel(millis: Long): String =
    java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US).format(java.util.Date(millis))
