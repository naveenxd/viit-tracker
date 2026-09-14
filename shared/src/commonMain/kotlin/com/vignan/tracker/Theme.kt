package com.vignan.tracker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object TrackerColors {
    val PureBlack = Color(0xFF000000)
    val SurfaceDark = Color(0xFF0D0E12)
    val SurfaceCard = Color(0xFF12151E)
    val SurfaceElevated = Color(0xFF181B26)
    val SurfaceInput = Color(0xFF151822)
    
    val HairlineBorder = Color(0xFF1E2230)
    val HairlineBorderLight = Color(0xFF2B3044)
    val BorderFocused = Color(0xFF6366F1)

    val PrimaryWhite = Color(0xFFFFFFFF)
    val PrimaryAccent = Color(0xFF6366F1)

    val SafeEmerald = Color(0xFF10B981)
    val SafeEmeraldSubtle = Color(0x1F10B981)

    val WarningAmber = Color(0xFFF59E0B)
    val WarningAmberSubtle = Color(0x1FF59E0B)

    val DangerRose = Color(0xFFF43F5E)
    val DangerRoseSubtle = Color(0x1FF43F5E)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    val TextSubtle = Color(0xFF475569)
}

private val DarkColorScheme = darkColorScheme(
    primary = TrackerColors.PrimaryAccent,
    onPrimary = Color.White,
    primaryContainer = TrackerColors.SurfaceElevated,
    onPrimaryContainer = TrackerColors.TextPrimary,
    background = TrackerColors.PureBlack,
    onBackground = TrackerColors.TextPrimary,
    surface = TrackerColors.SurfaceDark,
    onSurface = TrackerColors.TextPrimary,
    surfaceVariant = TrackerColors.SurfaceElevated,
    onSurfaceVariant = TrackerColors.TextSecondary,
    outline = TrackerColors.HairlineBorder,
    error = TrackerColors.DangerRose,
    onError = Color.White,
    errorContainer = TrackerColors.DangerRoseSubtle,
    onErrorContainer = TrackerColors.DangerRose
)

@Composable
fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
