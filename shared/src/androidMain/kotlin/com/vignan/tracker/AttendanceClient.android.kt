package com.vignan.tracker

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual object AttendanceClient {
    private const val BASE_URL = "http://10.0.2.2:3000"

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    actual suspend fun fetchAttendance(rollNumber: String, pass: String): AttendanceResponse {
        return client.get("$BASE_URL/attendance/${rollNumber.trim()}/$pass").body()
    }
}
