package com.vignan.tracker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
actual fun App() {
    TrackerTheme {
        AttendanceScreen()
    }
}
