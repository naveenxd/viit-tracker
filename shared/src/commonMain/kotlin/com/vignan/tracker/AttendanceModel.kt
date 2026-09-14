package com.vignan.tracker

import kotlinx.serialization.Serializable

@Serializable
data class SubjectAttendance(
    val code: String,
    val name: String,
    val attended: Int,
    val conducted: Int,
    val percentage: Double
)

@Serializable
data class AttendanceResponse(
    val studentName: String,
    val rollNumber: String,
    val overallPercentage: Double,
    val subjects: List<SubjectAttendance>
)
