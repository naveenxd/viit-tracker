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

    // Text — tuned for WCAG 2.1 contrast (the app is dark-mode only).
    // Ratios measured against SurfaceElevated (#171717), the lightest surface
    // text sits on. AA body >= 4.5:1, AAA body >= 7:1, non-text >= 3:1.
    //   TextPrimary   ~15.3:1  (AAA)
    //   TextSecondary  ~7.4:1  (AAA)
    //   TextMuted      ~5.2:1  (AA)
    //   TextSubtle     ~3.1:1  (large text / decorative only)
    val PrimaryWhite  = Color(0xFFFFFFFF)
    val PrimaryAccent = Color(0xFFFFFFFF)
    val TextPrimary   = Color(0xFFEDEDED)
    val TextSecondary = Color(0xFFA6A6A6)
    val TextMuted     = Color(0xFF8A8A8A)
    val TextSubtle    = Color(0xFF666666)

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
