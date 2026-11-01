package com.vignan.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceStatusTest {

    @Test
    fun safeStartsFivePointsAboveTheTarget() {
        assertEquals(AttendanceStatus.BORDER, attendanceStatus(79.9, targetPct = 75.0))
        assertEquals(AttendanceStatus.SAFE, attendanceStatus(80.0, targetPct = 75.0))
        assertEquals(AttendanceStatus.SAFE, attendanceStatus(91.0, targetPct = 75.0))
    }

    @Test
    fun borderIsAtOrSlightlyAboveTheTarget() {
        assertEquals(AttendanceStatus.BORDER, attendanceStatus(75.0, targetPct = 75.0))
        assertEquals(AttendanceStatus.BORDER, attendanceStatus(76.4, targetPct = 75.0))
        assertEquals(AttendanceStatus.DANGER, attendanceStatus(74.9, targetPct = 75.0))
    }

    @Test
    fun thresholdsFollowTheConfiguredTarget() {
        assertEquals(AttendanceStatus.DANGER, attendanceStatus(79.9, targetPct = 80.0))
        assertEquals(AttendanceStatus.BORDER, attendanceStatus(80.0, targetPct = 80.0))
        assertEquals(AttendanceStatus.BORDER, attendanceStatus(84.9, targetPct = 80.0))
        assertEquals(AttendanceStatus.SAFE, attendanceStatus(85.0, targetPct = 80.0))
    }

    @Test
    fun targetIsClampedToSaneBounds() {
        assertEquals(MIN_TARGET_PCT, normalizeTargetPct(-10.0))
        assertEquals(MAX_TARGET_PCT, normalizeTargetPct(200.0))
        assertEquals(DEFAULT_TARGET_PCT, normalizeTargetPct(Double.NaN))
        assertEquals(DEFAULT_TARGET_PCT, normalizeTargetPct(null))
    }

    @Test
    fun skippablePeriodsScaleWithTheTarget() {
        assertEquals(6, skippablePeriods(80, 100, targetPct = 75.0))
        assertEquals(0, skippablePeriods(80, 100, targetPct = 80.0))
        assertEquals(20, skippablePeriods(90, 100, targetPct = 75.0))
        assertEquals(12, skippablePeriods(90, 100, targetPct = 80.0))
        assertEquals(0, skippablePeriods(70, 100, targetPct = 75.0))
    }
}
