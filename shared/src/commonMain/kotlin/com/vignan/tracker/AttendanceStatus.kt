package com.vignan.tracker

import androidx.compose.ui.graphics.Color

/**
 * Canonical attendance verdict, shared by the dashboard, report, projection and
 * widget so the same percentage never gets three different labels.
 *
 * Geometry is relative to the user's target:
 * - [SAFE]   at or above target + 5
 * - [BORDER] at or above the target, but short of [SAFE]
 * - [DANGER] below the target
 */
enum class AttendanceStatus(val label: String) {
    SAFE("SAFE"),
    BORDER("BORDER"),
    DANGER("DANGER");

    val color: Color
        get() = when (this) {
            SAFE -> TrackerColors.SafeEmerald
            BORDER -> TrackerColors.WarningAmber
            DANGER -> TrackerColors.DangerRose
        }
}

/** College-mandated default. Used until the user picks something else. */
const val DEFAULT_TARGET_PCT = 75.0

/** Widest range the picker/store will honour; also guards corrupt saved values. */
const val MIN_TARGET_PCT = 50.0
const val MAX_TARGET_PCT = 95.0

/** Preset targets shown in Settings. */
val TARGET_PCT_OPTIONS = listOf(70.0, 75.0, 80.0, 85.0, 90.0)

/** Clamp a stored or requested target into a sane range. */
fun normalizeTargetPct(value: Double?): Double {
    val candidate = value?.takeIf { it.isFinite() } ?: DEFAULT_TARGET_PCT
    return candidate.coerceIn(MIN_TARGET_PCT, MAX_TARGET_PCT)
}

/** The one status calculation used everywhere. */
fun attendanceStatus(
    percentage: Double,
    targetPct: Double = AppSettings.targetPct
): AttendanceStatus {
    val target = normalizeTargetPct(targetPct)
    return when {
        percentage >= target + 5.0 -> AttendanceStatus.SAFE
        percentage >= target -> AttendanceStatus.BORDER
        else -> AttendanceStatus.DANGER
    }
}
