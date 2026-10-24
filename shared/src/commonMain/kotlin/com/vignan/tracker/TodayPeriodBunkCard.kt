package com.vignan.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Today-only period picker: the fine-grained sibling of "mark leaves".
 *
 * The day-level calendar says "I will miss all of today". This card says "I
 * will miss periods 3 and 4". Only periods the portal has not already booked
 * are tappable; the portal's today badges are the sole signal for what is done,
 * so a delayed scrape or a wrong timetable cannot hide a bunkable period.
 * Selected periods keep counting as held but not attended, which flows straight
 * into [ProjectionCard].
 */
@Composable
internal fun TodayPeriodBunkCard(
    liveResponse: LiveAttendanceResponse?,
    hasData: Boolean,
    modifier: Modifier = Modifier
) {
    // Period badges can land late, and the day can roll over while this card is
    // on screen. One refresh a minute keeps both the count and the day honest.
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val todayKey = projectionDateKey(nowMillis)
    LaunchedEffect(todayKey) { ProjectionMarks.ensurePeriodsDay(todayKey) }

    val isHoliday = todayKey in ProjectionMarks.holidayDates
    val isFullDayLeave = todayKey in ProjectionMarks.absentDates
    val isSunday = java.util.Calendar.getInstance()
        .apply { timeInMillis = nowMillis }
        .get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY &&
        !DEBUG_PERIOD_BUNK_ON_SUNDAY
    val blocked = isHoliday || isFullDayLeave || isSunday

    val bookedPeriods = remember(liveResponse, nowMillis) {
        liveResponse?.let { todayBookedPeriods(it, nowMillis) } ?: 0
    }
    val slots = remember(liveResponse, nowMillis, blocked) {
        todayPeriodSlots(liveResponse, nowMillis, blocked)
    }
    val remaining = remember(liveResponse, nowMillis) {
        todayRemainingPeriods(liveResponse, nowMillis)
    }

    // A newer snapshot can prove a picked period has already been recorded, and
    // a full-day mark makes individual picks moot. Drop those, but do not drop
    // picks merely because the clock passed them: they still shape today's
    // forecast until fresh portal data says otherwise.
    LaunchedEffect(remaining, blocked) {
        ProjectionMarks.retainPeriodsToday(if (blocked) emptySet() else remaining)
    }

    val selected = ProjectionMarks.bunkedPeriodsToday
    val selectedValid = selected intersect remaining
    val summary = remember(liveResponse, selectedValid, blocked, todayKey) {
        liveResponse?.let {
            todayBunkSummary(it, selectedValid, nowMillis, blocked)
        }
    }

    val anySelectable = slots.any { it.selectable }
    val statusText = when {
        isHoliday -> "Today is marked as a holiday."
        isFullDayLeave -> "Today is marked as a full-day leave."
        isSunday -> "Sunday · no classes to bunk."
        !hasData || liveResponse == null -> "Fetch attendance to pick today's periods."
        bookedPeriods >= SLOTS.size -> "All ${SLOTS.size} periods are already booked on the portal."
        selectedValid.isNotEmpty() -> buildString {
            append("${selectedValid.size} period")
            if (selectedValid.size != 1) append("s")
            append(" selected")
            summary?.let { append(" · after skip ${formatPercentage(it.percentage)}") }
        }
        !anySelectable -> "No periods left today."
        else -> "Portal shows $bookedPeriods booked · only later periods are open."
    }
    val statusColor = when {
        isHoliday || isFullDayLeave -> TrackerColors.WarningAmber
        selectedValid.isNotEmpty() -> summary
            ?.let { attendanceStatusColor(true, it.percentage) }
            ?: TrackerColors.TextSecondary
        else -> TrackerColors.TextMuted
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Select period to bunk today",
                color = TrackerColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                slots.forEach { slot ->
                    val isSelected = slot.number in selected
                    PeriodChip(
                        slot = slot,
                        selected = isSelected,
                        onClick = { ProjectionMarks.togglePeriodToday(slot.number) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun PeriodChip(
    slot: TodayPeriodSlot,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A booked period cannot be newly marked, but an already-marked one stays
    // tappable so the user can always take the mark back.
    val clickable = slot.selectable || selected
    val background = when {
        selected -> TrackerColors.PrimaryWhite
        slot.selectable -> TrackerColors.SurfaceElevated
        else -> TrackerColors.SurfaceInput
    }
    val foreground = when {
        selected -> TrackerColors.PureBlack
        slot.selectable -> if (TrackerColors.isLight) {
            TrackerColors.TextSecondary
        } else {
            TrackerColors.TextPrimary
        }
        else -> TrackerColors.TextMuted
    }
    val borderColor = if (selected) {
        TrackerColors.PrimaryWhite
    } else {
        TrackerColors.HairlineBorderLight
    }
    val description = buildString {
        append("Period ${slot.number}")
        when {
            selected -> append(", selected to bunk")
            slot.selectable -> append(", available")
            else -> append(", already booked on the portal")
        }
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(7.dp))
            .then(if (clickable) Modifier.pressClickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${slot.number}",
            color = foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}
