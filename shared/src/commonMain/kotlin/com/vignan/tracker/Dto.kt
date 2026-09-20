package com.vignan.tracker

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---- KV Remote Config ----

@Serializable
data class KvConfigResponse(
    val apiUrl: String
)

// ---- requests ----

@Serializable
data class LiveAttendanceRequest(
    val rollNo: String,
    val password: String,
    val targetPct: Double = 75.0,
)

@Serializable
data class CredentialsRequest(
    val rollNo: String,
    val password: String,
)

@Serializable
data class SimulateBunkRequest(
    val currentAttended: Int,
    val currentHeld: Int,
    val selectedPeriodsToday: List<Int>,
    val fullDaysLeave: Int = 0,
    val targetPct: Double = 75.0,
)

// ---- responses ----

@Serializable
data class ErrorResponse(
    val error: String,
    val details: String? = null
)

@Serializable
data class SubjectAttendance(
    val subject: String,
    val held: Int,
    val attended: Int,
    val percentage: Double,
)

@Serializable
data class ProfileInfo(
    val name: String,
    val rollNo: String,
    val branch: String,
    val semester: String,
    val aggregate: Aggregate,
    val subjects: List<SubjectAttendance>,
)

@Serializable
data class Aggregate(
    val held: Int,
    val attended: Int,
    val percentage: Double
)

@Serializable
data class SkipsInfo(
    val periods: Int,
    val days: Int,
    val remainingPeriods: Int,
    val projectedPercentage: Double,
    val status: String,          // "Safe" | "Critical"
    val classesNeededToRecover: Int,
)

@Serializable
data class ForecastDay(
    val date: String,
    val projectedAttended: Int,
    val projectedHeld: Int,
    val projectedPercentage: Double,
)

@Serializable
data class TimetablePeriod(
    val slotNumber: Int,
    val collegePeriod: String,
    val subject: String,
    val time: String,
)

@Serializable
data class TimetableDay(
    val day: String,
    val periods: List<TimetablePeriod>
)

@Serializable
data class FacultyAllocation(
    val code: String,
    val subject: String,
    val faculty: String
)

@Serializable
data class AttendanceSection(
    val subjects: List<SubjectAttendance>,
    val today: List<TodayAttendance> = emptyList(),
    val timetable: List<TimetableDay> = emptyList(),
    val faculty: List<FacultyAllocation> = emptyList(),
)

@Serializable
data class TodayAttendance(
    val date: String,
    val badges: List<Badge>
)

@Serializable
data class Badge(
    val subject: String,
    val status: String,
    val presentCount: Int,
    val absentCount: Int,
)

@Serializable
data class Intelligence(
    val targetPct: Double,
    val safeSkips: SkipsInfo,
    val forecast7Days: List<ForecastDay> = emptyList(),
)

@Serializable
data class LiveAttendanceResponse(
    val profile: ProfileInfo,
    val attendance: AttendanceSection,
    val intelligence: Intelligence,
    val scrapedAt: String,
)

@Serializable
data class SimulateBunkResponse(
    val current: Snapshot,
    val simulated: Simulated,
    val targetPct: Double,
)

@Serializable
data class Snapshot(
    val attended: Int,
    val held: Int,
    val percentage: Double
)

@Serializable
data class Simulated(
    val attended: Int,
    val held: Int,
    val percentage: Double,
    val dropInPercentage: Double,
    val meetsTarget: Boolean,
)

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}

fun LiveAttendanceResponse.toAttendanceResponse(): AttendanceResponse {
    val mappedSubjects = profile.subjects.map { s ->
        val parts = s.subject.split("-", limit = 2)
        val code = if (parts.size > 1) parts[0].trim() else ""
        val name = if (parts.size > 1) parts[1].trim() else s.subject.trim()
        UiSubjectAttendance(
            code = code,
            name = name,
            attended = s.attended,
            conducted = s.held,
            percentage = s.percentage
        )
    }
    return AttendanceResponse(
        studentName = profile.name,
        rollNumber = profile.rollNo,
        overallPercentage = profile.aggregate.percentage,
        subjects = mappedSubjects
    )
}

