package com.vignan.tracker

expect object AttendanceClient {
    suspend fun fetchAttendance(rollNumber: String, pass: String): AttendanceResponse
}
