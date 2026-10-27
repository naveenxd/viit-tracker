package com.vignan.tracker

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodayBunkSummaryTest {

    /** Monday, 2 Nov 2026, 10:00 local — a normal working day for the projection. */
    private val monday = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 2, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun pickedPeriodLowersPercentageInsteadOfAddingFutureAttendance() {
        val live = liveWithSevenPeriods(monday, attended = 80, held = 100)

        val summary = todayBunkSummary(live, setOf(5), monday)

        assertEquals(80, summary?.attended)
        assertEquals(101, summary?.held)
        assertTrue(summary!!.percentage < 80.0)
    }

    @Test
    fun noPicksLeavesTheCurrentPercentageAlone() {
        val live = liveWithSevenPeriods(monday, attended = 80, held = 100)

        val summary = todayBunkSummary(live, emptySet(), monday)

        assertEquals(80, summary?.attended)
        assertEquals(100, summary?.held)
        assertEquals(80.0, summary?.percentage)
    }

    @Test
    fun everyPickAddsExactlyOneHeldPeriod() {
        val live = liveWithSevenPeriods(monday, attended = 80, held = 100)

        val onePick = todayBunkSummary(live, setOf(5), monday)!!
        val twoPicks = todayBunkSummary(live, setOf(5, 6), monday)!!

        assertEquals(102, twoPicks.held)
        assertTrue(twoPicks.percentage < onePick.percentage)
    }

    private fun liveWithSevenPeriods(
        nowMillis: Long,
        attended: Int,
        held: Int
    ): LiveAttendanceResponse {
        val dayName = SimpleDateFormat("EEE", Locale.US).format(nowMillis)
        val periods = (1..7).map { slot ->
            TimetablePeriod(
                slotNumber = slot,
                collegePeriod = slot.toString(),
                subject = "22CSB3103-OOPS",
                time = ""
            )
        }
        return LiveAttendanceResponse(
            profile = ProfileInfo(
                name = "Test",
                rollNo = "22CSB3103",
                branch = "CSE",
                semester = "5",
                aggregate = Aggregate(
                    held = held,
                    attended = attended,
                    percentage = attended * 100.0 / held
                ),
                subjects = emptyList()
            ),
            attendance = AttendanceSection(
                subjects = emptyList(),
                timetable = listOf(TimetableDay(day = dayName, periods = periods))
            ),
            intelligence = Intelligence(
                targetPct = 75.0,
                safeSkips = SkipsInfo(
                    periods = 0,
                    days = 0,
                    remainingPeriods = 0,
                    projectedPercentage = 0.0,
                    status = "Safe",
                    classesNeededToRecover = 0
                )
            ),
            scrapedAt = ""
        )
    }
}
