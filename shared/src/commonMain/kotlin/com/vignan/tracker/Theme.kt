package com.vignan.tracker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object TrackerColors {
    // Backgrounds
    val PureBlack       = Color(0xFF000000)
    val SurfaceDark     = Color(0xFF0A0A0A)
    val SurfaceCard     = Color(0xFF111111)
    val SurfaceElevated = Color(0xFF171717)
    val SurfaceInput    = Color(0xFF0D0D0D)

    // Borders
    val HairlineBorder      = Color(0xFF1C1C1C)
    val HairlineBorderLight = Color(0xFF262626)
    val BorderFocused       = Color(0xFF404040)

    // Text
    val PrimaryWhite  = Color(0xFFFFFFFF)
    val PrimaryAccent = Color(0xFFFFFFFF)
    val TextPrimary   = Color(0xFFEDEDED)
    val TextSecondary = Color(0xFF8C8C8C)
    val TextMuted     = Color(0xFF555555)
    val TextSubtle    = Color(0xFF383838)

    // Status
    val SafeEmerald        = Color(0xFF22C55E)
    val SafeEmeraldSubtle  = Color(0x1522C55E)
    val WarningAmber       = Color(0xFFF59E0B)
    val WarningAmberSubtle = Color(0x1FF59E0B)
    val DangerRose         = Color(0xFFEF4444)
    val DangerRoseSubtle   = Color(0x15EF4444)
}

private val DarkColorScheme = darkColorScheme(
    primary            = TrackerColors.PrimaryWhite,
    onPrimary          = TrackerColors.PureBlack,
    primaryContainer   = TrackerColors.SurfaceElevated,
    onPrimaryContainer = TrackerColors.TextPrimary,
    background         = TrackerColors.PureBlack,
    onBackground       = TrackerColors.TextPrimary,
    surface            = TrackerColors.SurfaceDark,
    onSurface          = TrackerColors.TextPrimary,
    surfaceVariant     = TrackerColors.SurfaceElevated,
    onSurfaceVariant   = TrackerColors.TextSecondary,
    outline            = TrackerColors.HairlineBorder,
    error              = TrackerColors.DangerRose,
    onError            = Color.White,
    errorContainer     = TrackerColors.DangerRoseSubtle,
    onErrorContainer   = TrackerColors.DangerRose
)

@Composable
fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
