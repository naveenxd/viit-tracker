package com.vignan.tracker

import kotlinx.serialization.Serializable

/**
 * POST /api/attendance/report — the portal's official ATTENDANCE REPORT
 * (ShowAttendance page). Dates use the portal's DD/MM/YYYY format; both are
 * optional — omitting them returns the semester-to-date report.
 */
@Serializable
data class AttendanceReportRequest(
    val rollNo: String,
    val password: String,
    val fromDate: String? = null,
    val toDate: String? = null,
    val excludeOthers: Boolean = false
)

@Serializable
data class ReportStudent(
    val rollNo: String = "",
    val name: String = "",
    val course: String = "",
    val branch: String = "",
    val semester: String = ""
)

@Serializable
data class ReportSubject(
    val subject: String,
    val held: Int,
    val attended: Int,
    val percentage: Double
)

@Serializable
data class ReportAggregate(
    val held: Int,
    val attended: Int,
    val percentage: Double
)

@Serializable
data class AttendanceReportResponse(
    val student: ReportStudent = ReportStudent(),
    val subjects: List<ReportSubject> = emptyList(),
    val aggregate: ReportAggregate = ReportAggregate(0, 0, 0.0),
    val scrapedAt: String = ""
)
