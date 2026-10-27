package com.vignan.tracker

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodayBunkSummaryTest {

    /** Monday, 2 Nov 2026, 10:00 local — a normal working day for the projection. */
    private val monday = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 2, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Sunday, 1 Nov 2026, 10:00 local — off unless Working Sundays is enabled. */
    private val sunday = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 1, 10, 0, 0)
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

    @Test
    fun sundayIsOffUntilWorkingSundaysIsEnabled() {
        val live = liveWithSevenPeriods(sunday, attended = 80, held = 100)
        val cal = Calendar.getInstance().apply { timeInMillis = sunday }

        assertTrue(isSundayOffDay(cal, sundayClassesEnabled = false))
        assertNull(todayBunkSummary(live, setOf(1), sunday, sundayClassesEnabled = false))

        assertFalse(isSundayOffDay(cal, sundayClassesEnabled = true))
        assertEquals(101, todayBunkSummary(live, setOf(1), sunday, sundayClassesEnabled = true)?.held)
    }

    @Test
    fun projectionKeepsSundayOffEvenWhenWorkingSundaysIsEnabled() {
        val live = liveWithSevenPeriods(sunday, attended = 80, held = 100)

        val row = buildAttendanceProjection(
            live = live,
            days = 1,
            nowMillis = sunday
        ).single()

        assertTrue(row.isSunday)
        assertEquals(0, row.classesThatDay)
    }

    @Test
    fun workingSundaySummaryUsesPickerPeriodsWithoutASundayTimetableRow() {
        // The timetable has no Sunday row, only Monday. The period picker still
        // offers all seven slots; the summary must count them all.
        val live = liveWithSevenPeriods(
            nowMillis = sunday,
            attended = 80,
            held = 100,
            timetableDay = "Mon"
        )

        val summary = todayBunkSummary(
            live = live,
            bunkedPeriods = (1..7).toSet(),
            nowMillis = sunday,
            sundayClassesEnabled = true
        )

        assertEquals(7, summary?.bunkedPeriods)
        assertEquals(80, summary?.attended)
        assertEquals(107, summary?.held)
    }

    private fun liveWithSevenPeriods(
        nowMillis: Long,
        attended: Int,
        held: Int,
        timetableDay: String = SimpleDateFormat("EEE", Locale.US).format(nowMillis)
    ): LiveAttendanceResponse {
        val dayName = timetableDay
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
