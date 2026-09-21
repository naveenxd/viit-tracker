package com.vignan.tracker

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AttendanceScreen() {
    val repository = remember { AttendanceRepository() }
    val scope = rememberCoroutineScope()

    var rollNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }

    var isInitializing by remember { mutableStateOf(true) }
    var isSessionActive by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var liveAttendanceResponse by remember { mutableStateOf<LiveAttendanceResponse?>(null) }
    var attendanceData by remember { mutableStateOf<AttendanceResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showValidationDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Instant launch: Load stored session & cache immediately without showing login screen
    LaunchedEffect(Unit) {
        val stored = repository.getStoredCredentials()
        val cached = repository.getCachedAttendanceResponse()
        val now = System.currentTimeMillis()

        if (stored != null && repository.isSessionValid(stored, now)) {
            rollNumber = stored.rollNo
            password = stored.password
            rememberMe = stored.rememberMe
            isSessionActive = true

            if (cached != null) {
                liveAttendanceResponse = cached
                attendanceData = cached.toAttendanceResponse()
            }

            isInitializing = false
            isLoading = true

            scope.launch {
                val result = repository.fetchLiveAttendance(
                    rollNo = stored.rollNo,
                    password = stored.password,
                    rememberMe = stored.rememberMe,
                    currentTimeMs = now
                )
                result.fold(
                    onSuccess = { liveResp ->
                        liveAttendanceResponse = liveResp
                        attendanceData = liveResp.toAttendanceResponse()
                    },
                    onFailure = { error ->
                        val friendlyMsg = mapApiErrorToUserMessage(error)
                        if (attendanceData == null) {
                            errorMessage = friendlyMsg
                            isSessionActive = false
                        }
                        snackbarHostState.showSnackbar(
                            message = friendlyMsg,
                            duration = SnackbarDuration.Short
                        )
                    }
                )
                isLoading = false
            }
        } else {
            if (stored != null) {
                rollNumber = stored.rollNo
                rememberMe = stored.rememberMe
            }
            isSessionActive = false
            isInitializing = false
        }
    }

    fun fetchAttendance() {
        if (rollNumber.isBlank() || password.isBlank()) {
            showValidationDialog = true
            return
        }

        isSessionActive = true
        isLoading = true
        errorMessage = null

        scope.launch {
            val result = repository.fetchLiveAttendance(
                rollNo = rollNumber,
                password = password,
                rememberMe = rememberMe
            )
            result.fold(
                onSuccess = { liveResp ->
                    liveAttendanceResponse = liveResp
                    attendanceData = liveResp.toAttendanceResponse()
                },
                onFailure = { error ->
                    val friendlyMsg = mapApiErrorToUserMessage(error)
                    errorMessage = friendlyMsg
                    if (attendanceData == null) {
                        isSessionActive = false
                    }
                    snackbarHostState.showSnackbar(
                        message = friendlyMsg,
                        duration = SnackbarDuration.Short
                    )
                }
            )
            isLoading = false
        }
    }

    if (showValidationDialog) {
        AlertDialog(
            onDismissRequest = { showValidationDialog = false },
            containerColor = TrackerColors.SurfaceDark,
            titleContentColor = TrackerColors.TextPrimary,
            textContentColor = TrackerColors.TextSecondary,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TrackerColors.DangerRose)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MISSING CREDENTIALS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Please enter both your Registration Number and Password to login.",
                    fontSize = 12.sp,
                    color = TrackerColors.TextSecondary
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TrackerColors.PrimaryWhite)
                        .clickable { showValidationDialog = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "OK",
                        color = TrackerColors.PureBlack,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        )
    }

    Scaffold(
        containerColor = TrackerColors.PureBlack,
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TrackerColors.SurfaceDark)
                        .border(1.dp, TrackerColors.DangerRose.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(TrackerColors.DangerRose)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = data.visuals.message,
                            color = TrackerColors.TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        topBar = {
            HeaderBar(
                isLoggedIn = isSessionActive,
                onLogout = {
                    scope.launch { repository.logout() }
                    isSessionActive = false
                    liveAttendanceResponse = null
                    attendanceData = null
                    password = ""
                    errorMessage = null
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(TrackerColors.PureBlack)
        ) {
            val screenState = when {
                isInitializing -> MainScreenState.INITIALIZING
                !isSessionActive -> MainScreenState.LOGIN
                else -> MainScreenState.DASHBOARD
            }

            Crossfade(
                targetState = screenState,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                label = "mainScreenTransition"
            ) { state ->
                when (state) {
                    MainScreenState.INITIALIZING -> {
                        // Brief check for a stored session — just a tiny spinner.
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = TrackerColors.TextMuted,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    MainScreenState.LOGIN -> {
                        LoginScreen(
                            rollNumber = rollNumber,
                            onRollNumberChange = { rollNumber = it },
                            password = password,
                            onPasswordChange = { password = it },
                            isPasswordVisible = isPasswordVisible,
                            onTogglePasswordVisibility = { isPasswordVisible = !isPasswordVisible },
                            rememberMe = rememberMe,
                            onRememberMeChange = { rememberMe = it },
                            isLoading = isLoading,
                            onSubmit = { fetchAttendance() }
                        )
                    }
                    MainScreenState.DASHBOARD -> {
                        // Real UI renders immediately; data fills in when the response lands.
                        DashboardScreen(
                            data = attendanceData,
                            liveResponse = liveAttendanceResponse,
                            isRefreshing = isLoading,
                            onFetchClick = { fetchAttendance() }
                        )
                    }
                }
            }
        }
    }
}

private enum class MainScreenState {
    INITIALIZING,
    LOGIN,
    DASHBOARD
}

private fun mapApiErrorToUserMessage(error: Throwable): String {
    val cause = (error as? ApiError.Network)?.cause
    println("ATTENDANCE_ERROR_DEBUG: ${error::class.simpleName}: ${error.message}, cause: ${cause?.message}")
    cause?.printStackTrace()
    return when (error) {
        is ApiError.InvalidCredentials -> if (error.message.isNotBlank()) error.message else "Wrong roll number or password."
        is ApiError.RateLimited -> "Too many attempts. Try again in ${error.retryAfterSec}s."
        is ApiError.Network -> "Connection error: ${cause?.message ?: cause?.toString() ?: "Unable to reach server"}"
        is ApiError.Upstream -> error.message.ifBlank { "Attendance service is unavailable." }
        is ApiError.BadRequest -> error.message.ifBlank { "Invalid request." }
        is ApiError.Unknown -> "Error ${error.code}: ${error.body}"
        else -> error.message?.ifBlank { null } ?: "Attendance service is unavailable."
    }
}
