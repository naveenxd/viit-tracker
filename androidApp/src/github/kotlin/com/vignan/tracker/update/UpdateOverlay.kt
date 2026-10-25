package com.vignan.tracker.update

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignan.tracker.Motion
import com.vignan.tracker.TrackerColors
import com.vignan.tracker.motionTween
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class ToastState {
    Checking,
    UpToDate,
    Failed,
}

private enum class SheetPhase { Idle, Downloading, Verifying, Ready, Error }

/**
 * Wraps app content with the in-app updater:
 *
 * - runs an update check on **every app open**,
 * - shows a floating status card above the tab bar
 *   ("Checking for updates" → "You're up to date" / "Couldn't check for
 *   updates"), with a tap-to-retry action when the check fails,
 * - shows a mirai-style bottom sheet with the release changelog when a newer
 *   release exists — download with live progress, SHA-256 verification, then
 *   hand off to the system installer,
 * - fails softly: a failed check only shows the status card, never blocks the
 *   app.
 */
@Composable
fun UpdateOverlay(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current

    val localVersion = remember { UpdateChecker.currentVersionName(context) }

    var toast by remember { mutableStateOf<ToastState?>(ToastState.Checking) }
    var statusCardState by remember { mutableStateOf(ToastState.Checking) }
    var available by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkAttempt by remember { mutableIntStateOf(0) }

    // Keep the last state around so the exit animation has something to draw
    // after `toast` becomes null.
    LaunchedEffect(toast) {
        toast?.let { statusCardState = it }
    }

    // Update check on every open (fresh composition per activity launch).
    // Retrying a failed check bumps [checkAttempt], which cancels the old delay
    // and starts this effect over.
    LaunchedEffect(checkAttempt) {
        toast = ToastState.Checking
        when (val result = UpdateChecker.check(context)) {
            is UpdateResult.Available -> {
                toast = null
                available = result.info
            }
            UpdateResult.UpToDate -> {
                toast = ToastState.UpToDate
                delay(2_600)
                toast = null
            }
            UpdateResult.CheckFailed -> {
                toast = ToastState.Failed
                // Long enough to notice the retry affordance, short enough that
                // it never becomes part of the screen.
                delay(6_000)
                toast = null
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        // ---------- bottom status toast ----------
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(motionTween(Motion.Base)) +
                slideInVertically(motionTween(Motion.Base)) { it / 3 },
            exit = fadeOut(motionTween(Motion.Fast)) +
                slideOutVertically(motionTween(Motion.Fast)) { it / 4 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                // Clear the floating tab pill (50dp) and its 16dp bottom gap,
                // plus a little breathing room, so the toast never sits on the
                // navigation.
                .padding(bottom = 86.dp),
        ) {
            StatusToast(
                state = statusCardState,
                onRetry = { checkAttempt++ },
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }

        // ---------- update bottom sheet ----------
        available?.let { info ->
            UpdateSheet(
                info = info,
                localVersion = localVersion,
                onDismiss = { available = null },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  STATUS TOAST
// ─────────────────────────────────────────────────────────────

@Composable
private fun StatusToast(
    state: ToastState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (state) {
        ToastState.Checking -> TrackerColors.TextSecondary
        ToastState.UpToDate -> TrackerColors.SafeEmerald
        ToastState.Failed -> TrackerColors.WarningAmber
    }
    val canRetry = state == ToastState.Failed

    Surface(
        modifier = modifier.then(
            if (canRetry) Modifier.clickable(onClick = onRetry) else Modifier
        ),
        shape = RoundedCornerShape(50),
        color = TrackerColors.SurfaceElevated,
        border = BorderStroke(1.dp, TrackerColors.HairlineBorder),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                ToastState.Checking -> CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.4.dp,
                    color = TrackerColors.TextSecondary,
                )

                ToastState.UpToDate, ToastState.Failed -> Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accent, CircleShape),
                )
            }

            Spacer(Modifier.width(8.dp))

            // Only the copy cross-fades; the dot or spinner stays in place.
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(motionTween(Motion.Base, delayMillis = Motion.Fast / 2))
                        .togetherWith(fadeOut(motionTween(Motion.Fast)))
                },
                label = "updateToastCopy",
            ) { current ->
                Text(
                    text = current.title(),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TrackerColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (canRetry) {
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Retry",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TrackerColors.WarningAmber,
                )
            }
        }
    }
}

private fun ToastState.title(): String = when (this) {
    ToastState.Checking -> "Checking for updates"
    ToastState.UpToDate -> "Up to date"
    ToastState.Failed -> "Couldn't check"
}

// ─────────────────────────────────────────────────────────────
//  UPDATE BOTTOM SHEET
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSheet(
    info: UpdateInfo,
    localVersion: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(SheetPhase.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val startDownload = {
        val url = info.apkUrl
        if (url.isNotBlank()) {
            phase = SheetPhase.Downloading
            progress = 0f
            downloaded = null
            downloadJob = scope.launch {
                try {
                    val apk = UpdateInstaller
                        .downloadApk(context, url) { progress = it }
                    phase = SheetPhase.Verifying
                    UpdateInstaller.verifyChecksum(apk, info.apkSha256)
                    downloaded = apk.file
                    phase = SheetPhase.Ready
                    UpdateInstaller.install(context, apk.file, info.notesUrl)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    downloaded = null
                    phase = SheetPhase.Error
                }
            }
        } else {
            UpdateInstaller.openUrl(context, info.notesUrl)
            onDismiss()
        }
    }

    val cancelDownload = {
        // Partial file is deleted on the next download; nothing to clean up here.
        downloadJob?.cancel()
        downloadJob = null
        progress = 0f
        phase = SheetPhase.Idle
    }

    ModalBottomSheet(
        onDismissRequest = {
            // Forced updates stay up; a normal dismiss mid-download cancels it.
            if (!info.forceUpdate) {
                if (phase == SheetPhase.Downloading || phase == SheetPhase.Verifying) {
                    downloadJob?.cancel()
                }
                onDismiss()
            }
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        contentColor = TrackerColors.TextPrimary,
        dragHandle = null,
        scrimColor = TrackerColors.PureBlack.copy(alpha = 0.66f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Surface(
            color = TrackerColors.SurfaceCard,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp)
                    .navigationBarsPadding(),
            ) {
                // ── Drag handle ──
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp)
                        .background(
                            TrackerColors.HairlineBorderLight,
                            RoundedCornerShape(50),
                        ),
                )

                Spacer(Modifier.height(18.dp))

                // ── Header: icon + title + version badge ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    rememberLauncherIcon()?.let { icon ->
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (info.forceUpdate) {
                                "Update required"
                            } else {
                                "Update available"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TrackerColors.TextPrimary,
                        )
                        VersionBadge(info = info, localVersion = localVersion)
                    }
                }

                // ── What's new ──
                if (info.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "WHAT'S NEW",
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        color = TrackerColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        info.changelog.forEach { item -> ChangelogBullet(item) }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Action area ──
                when (phase) {
                    SheetPhase.Idle -> UpdateButton(
                        label = if (info.apkUrl.isBlank()) {
                            "Open in browser"
                        } else {
                            "Download update"
                        },
                        withDownloadGlyph = info.apkUrl.isNotBlank(),
                        onClick = startDownload,
                    )

                    SheetPhase.Downloading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Downloading…",
                                fontSize = 12.sp,
                                color = TrackerColors.TextSecondary,
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TrackerColors.PrimaryWhite,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = TrackerColors.PrimaryWhite,
                            trackColor = TrackerColors.HairlineBorder,
                            gapSize = 0.dp,
                        )
                        // Forced updates keep running — no escape hatch.
                        if (!info.forceUpdate) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TrackerColors.TextMuted,
                                    modifier = Modifier
                                        .clickable(onClick = cancelDownload)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    SheetPhase.Verifying -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.8.dp,
                            color = TrackerColors.TextSecondary,
                        )
                        Text(
                            text = "Verifying download…",
                            fontSize = 12.5.sp,
                            color = TrackerColors.TextSecondary,
                        )
                    }

                    SheetPhase.Ready -> UpdateButton(
                        label = "Install now",
                        withDownloadGlyph = false,
                        onClick = {
                            downloaded?.let {
                                UpdateInstaller.install(context, it, info.notesUrl)
                            }
                        },
                    )

                    SheetPhase.Error -> {
                        Text(
                            text = "Download failed. Check your connection and retry.",
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = TrackerColors.DangerRose,
                        )
                        Spacer(Modifier.height(12.dp))
                        UpdateButton(
                            label = "Try again",
                            withDownloadGlyph = false,
                            onClick = startDownload,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SMALL WIDGETS
// ─────────────────────────────────────────────────────────────

/**
 * The launcher icon as an [ImageBitmap]. `painterResource(R.mipmap.ic_launcher)`
 * crashes on API 26+ (the anydpi resource is an adaptive-icon XML, which
 * Compose's painter loader rejects) — drawing the Drawable onto a Bitmap
 * works on every API level.
 */
@Composable
private fun rememberLauncherIcon(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 192, 192)
            drawable.draw(Canvas(bitmap))
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}

/** `v1.0 → v1.1 · 38 MB` — where you are, where you're going, what it costs. */
@Composable
private fun VersionBadge(info: UpdateInfo, localVersion: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "v$localVersion",
            fontSize = 12.sp,
            color = TrackerColors.TextMuted,
        )
        Text(
            text = "  →  ",
            fontSize = 11.sp,
            color = TrackerColors.TextSubtle,
        )
        Text(
            text = "v${info.version}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TrackerColors.PrimaryWhite,
        )
    }
}

@Composable
private fun ChangelogBullet(item: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .background(TrackerColors.PrimaryWhite, CircleShape),
        )
        Text(
            text = item,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = TrackerColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Full-width primary action, mirai-style, with a hand-drawn download glyph. */
@Composable
private fun UpdateButton(
    label: String,
    withDownloadGlyph: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = TrackerColors.PrimaryWhite,
        contentColor = TrackerColors.PureBlack,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (withDownloadGlyph) {
                DownloadGlyph(
                    modifier = Modifier.size(17.dp),
                    color = TrackerColors.PureBlack,
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Minimal download arrow: stem, chevron, tray. */
@Composable
private fun DownloadGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(
            color = color,
            start = Offset(w / 2f, h * 0.10f),
            end = Offset(w / 2f, h * 0.60f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawPath(
            path = Path().apply {
                moveTo(w * 0.24f, h * 0.44f)
                lineTo(w / 2f, h * 0.68f)
                lineTo(w * 0.76f, h * 0.44f)
            },
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawLine(
            color = color,
            start = Offset(w * 0.22f, h * 0.88f),
            end = Offset(w * 0.78f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}


