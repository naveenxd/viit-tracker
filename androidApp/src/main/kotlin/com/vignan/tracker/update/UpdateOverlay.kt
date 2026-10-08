package com.vignan.tracker.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignan.tracker.TrackerColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class UpdateStatus { Idle, Checking, UpToDate }

/**
 * Wraps app content with the in-app updater:
 *
 * - runs an update check on **every app open**,
 * - shows a minimal floating pill at the bottom while checking
 *   ("Checking for updates…" → "Up to date", auto-hides),
 * - shows an update popup with the release changelog when a newer release
 *   exists (download + hand off to the system installer),
 * - stays fully silent on network failures.
 */
@Composable
fun UpdateOverlay(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val localVersion = remember { UpdateChecker.currentVersionName(context) }

    var status by remember { mutableStateOf(UpdateStatus.Checking) }
    var available by remember { mutableStateOf<ReleaseInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Update check on every open (fresh composition per activity launch).
    LaunchedEffect(Unit) {
        status = UpdateStatus.Checking
        when (val result = UpdateChecker.check(localVersion)) {
            is UpdateResult.Available -> {
                status = UpdateStatus.Idle
                available = result.info
            }
            UpdateResult.UpToDate -> {
                status = UpdateStatus.UpToDate
                delay(2_600)
                status = UpdateStatus.Idle
            }
            UpdateResult.CheckFailed -> status = UpdateStatus.Idle
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        // ---------- bottom floating status pill ----------
        AnimatedVisibility(
            visible = status != UpdateStatus.Idle,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            StatusPill(status)
        }

        // ---------- update popup ----------
        available?.let { info ->
            UpdateDialog(
                info = info,
                localVersion = localVersion,
                downloading = downloading,
                progress = progress,
                onUpdate = {
                    val apk = info.apkUrl
                    if (apk == null) {
                        UpdateInstaller.openUrl(context, info.notesUrl)
                        available = null
                    } else {
                        downloading = true
                        progress = 0f
                        scope.launch {
                            try {
                                val file = UpdateInstaller
                                    .downloadApk(context, apk) { p -> progress = p }
                                downloading = false
                                available = null
                                UpdateInstaller.install(context, file, info.notesUrl)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // Download/install failed — fall back to the browser.
                                downloading = false
                                available = null
                                UpdateInstaller.openUrl(context, info.notesUrl)
                            }
                        }
                    }
                },
                onDismiss = { if (!downloading) available = null },
            )
        }
    }
}

@Composable
private fun StatusPill(status: UpdateStatus) {
    Surface(
        shape = CircleShape,
        color = TrackerColors.SurfaceElevated,
        border = BorderStroke(1.dp, TrackerColors.HairlineBorderLight),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (status) {
                UpdateStatus.Checking -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = TrackerColors.TextSecondary,
                    )
                    Text(
                        text = "Checking for updates…",
                        fontSize = 12.sp,
                        color = TrackerColors.TextSecondary,
                    )
                }
                UpdateStatus.UpToDate -> {
                    Text(
                        text = "✓",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TrackerColors.SafeEmerald,
                    )
                    Text(
                        text = "Up to date",
                        fontSize = 12.sp,
                        color = TrackerColors.TextPrimary,
                    )
                }
                UpdateStatus.Idle -> Unit
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    info: ReleaseInfo,
    localVersion: String,
    downloading: Boolean,
    progress: Float,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TrackerColors.SurfaceElevated,
        titleContentColor = TrackerColors.TextPrimary,
        textContentColor = TrackerColors.TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Update available",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = info.version,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TrackerColors.TextPrimary,
                    )
                    Text(
                        text = "· you have $localVersion",
                        fontSize = 13.sp,
                        color = TrackerColors.TextMuted,
                    )
                }

                if (info.changelog.isBlank()) {
                    Text(
                        text = "No release notes for this version.",
                        fontSize = 13.sp,
                        color = TrackerColors.TextMuted,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CHANGELOG",
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = TrackerColors.TextMuted,
                        )
                        Column(
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = info.changelog,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = TrackerColors.TextSecondary,
                            )
                        }
                    }
                }

                if (downloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = TrackerColors.PrimaryWhite,
                            trackColor = TrackerColors.HairlineBorderLight,
                        )
                        Text(
                            text = if (progress >= 1f) {
                                "Starting installer…"
                            } else {
                                "Downloading… ${(progress * 100).toInt()}%"
                            },
                            fontSize = 11.sp,
                            color = TrackerColors.TextMuted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onUpdate,
                enabled = !downloading,
            ) {
                Text(
                    text = if (info.apkUrl == null) "Open in browser" else "Update",
                    fontWeight = FontWeight.SemiBold,
                    color = TrackerColors.PrimaryWhite,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !downloading,
            ) {
                Text(
                    text = "Later",
                    color = TrackerColors.TextSecondary,
                )
            }
        },
    )
}
