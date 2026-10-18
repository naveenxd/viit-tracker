package com.vignan.tracker

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

/**
 * The looks the app ships with, as offered in Settings.
 *
 * [SYSTEM] is the default: the device's own dark-mode setting decides,
 * resolving to [DARK] or [LIGHT]. [AMOLED] is opt-in only — a fourth row that
 * unfolds under [DARK] while that branch is open — so true black (page #000,
 * surfaces within 0x00–0x17) stays available without being anyone's default.
 *
 * [DARK] keeps the same structure as AMOLED but lifts every surface a visible
 * step off the page — the way most consumer apps render dark mode — so cards
 * read as cards without a bright accent. [LIGHT] is deliberately not
 * white-on-white: a soft grey page under off-white cards, with the accent
 * inverted to ink.
 */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "System default"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    AMOLED("amoled", "Amoled dark");

    companion object {
        /** Unknown or missing ids fall back to the default. */
        fun fromId(id: String?): ThemeMode =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * One complete set of the app's colour tokens.
 *
 * The tokens keep their original names for the ~300 call sites that already
 * use them; what each name means is a *role*, not a literal colour:
 *
 *   pureBlack        furthest-back surface — the page background
 *   surfaceDark      a standard card sitting on the page
 *   surfaceCard      a card lifted one more step (hero blocks)
 *   surfaceElevated  inset strips, chips and controls *inside* a card
 *   surfaceInput     text fields and dropdowns
 *   primaryWhite     the strongest accent (filled buttons, selected pills,
 *                    progress fill) — inverted to ink in light mode
 *   textPrimary..    body → decorative, in descending emphasis
 *   safeEmerald / warningAmber / dangerRose  verdict colours + their tints
 *
 * Each mode is written out in full rather than derived by transforming the
 * dark palette: light mode needs darker status hues and an inverted accent,
 * which no blanket filter produces.
 */
private class Palette(
    val isLight: Boolean,
    val pureBlack: Color,
    val surfaceDark: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val surfaceInput: Color,
    val hairlineBorder: Color,
    val hairlineBorderLight: Color,
    val borderFocused: Color,
    val primaryWhite: Color,
    val primaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val safeEmerald: Color,
    val safeEmeraldSubtle: Color,
    val warningAmber: Color,
    val warningAmberSubtle: Color,
    val dangerRose: Color,
    val dangerRoseSubtle: Color
)

/** True black — the shipping look. Every surface sits within 0x00–0x17. */
private val AmoledPalette = Palette(
    isLight = false,
    pureBlack = Color(0xFF000000),
    surfaceDark = Color(0xFF0A0A0A),
    surfaceCard = Color(0xFF111111),
    surfaceElevated = Color(0xFF171717),
    surfaceInput = Color(0xFF0D0D0D),
    hairlineBorder = Color(0xFF1C1C1C),
    hairlineBorderLight = Color(0xFF262626),
    borderFocused = Color(0xFF404040),
    primaryWhite = Color(0xFFFFFFFF),
    primaryAccent = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFEDEDED),
    textSecondary = Color(0xFFA6A6A6),
    textMuted = Color(0xFF8A8A8A),
    textSubtle = Color(0xFF666666),
    safeEmerald = Color(0xFF22C55E),
    safeEmeraldSubtle = Color(0x1522C55E),
    warningAmber = Color(0xFFF59E0B),
    warningAmberSubtle = Color(0x1FF59E0B),
    dangerRose = Color(0xFFEF4444),
    dangerRoseSubtle = Color(0x15EF4444)
)

/**
 * Ordinary dark mode: a soft-black page (#0E0E0F) under grey cards, with
 * hairlines bright enough to be seen. Same hues as AMOLED, so nothing else in
 * the app has to move — only the elevation ladder changes.
 */
private val DarkPalette = Palette(
    isLight = false,
    pureBlack = Color(0xFF0E0E0F),
    surfaceDark = Color(0xFF1A1A1C),
    surfaceCard = Color(0xFF202023),
    surfaceElevated = Color(0xFF27272A),
    surfaceInput = Color(0xFF161618),
    hairlineBorder = Color(0xFF2C2C30),
    hairlineBorderLight = Color(0xFF3A3A3F),
    borderFocused = Color(0xFF54545A),
    primaryWhite = Color(0xFFFFFFFF),
    primaryAccent = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFF2F2F3),
    textSecondary = Color(0xFFB0B0B4),
    textMuted = Color(0xFF8B8B90),
    textSubtle = Color(0xFF6B6B70),
    safeEmerald = Color(0xFF22C55E),
    safeEmeraldSubtle = Color(0x1522C55E),
    warningAmber = Color(0xFFF59E0B),
    warningAmberSubtle = Color(0x1FF59E0B),
    dangerRose = Color(0xFFEF4444),
    dangerRoseSubtle = Color(0x15EF4444)
)

/**
 * Light mode without the glare: a cool grey page (#F3F4F6) rather than white,
 * near-white but never #FFFFFF cards, and ink (#17191D) wherever the dark
 * themes use white. Status hues drop to their 600/700 weights so they still
 * clear 4.5:1 on a light card.
 *
 * Contrast on the card surface (#FCFCFD):
 *   ink accent ~15:1 · textPrimary ~15:1 · textSecondary ~7.6:1
 *   textMuted ~5.0:1 · textSubtle ~3.3:1 (decorative)
 *   emerald ~4.7:1 · amber ~5.0:1 · rose ~4.4:1
 */
private val LightPalette = Palette(
    isLight = true,
    pureBlack = Color(0xFFF3F4F6),
    surfaceDark = Color(0xFFFCFCFD),
    surfaceCard = Color(0xFFFEFEFE),
    surfaceElevated = Color(0xFFEDEFF2),
    surfaceInput = Color(0xFFFDFDFE),
    hairlineBorder = Color(0xFFE3E6EA),
    hairlineBorderLight = Color(0xFFD5D9DF),
    borderFocused = Color(0xFF9AA1AB),
    primaryWhite = Color(0xFF17191D),
    primaryAccent = Color(0xFF17191D),
    textPrimary = Color(0xFF191C20),
    textSecondary = Color(0xFF4F555E),
    textMuted = Color(0xFF6C727B),
    textSubtle = Color(0xFF8A9099),
    safeEmerald = Color(0xFF15803D),
    safeEmeraldSubtle = Color(0x1F15803D),
    warningAmber = Color(0xFFB45309),
    warningAmberSubtle = Color(0x1FB45309),
    dangerRose = Color(0xFFDC2626),
    dangerRoseSubtle = Color(0x1FDC2626)
)

private fun paletteFor(mode: ThemeMode): Palette = when (mode) {
    // SYSTEM reads the device flag as plain state, so a single snapshot read
    // anywhere in the tree repaints everything when the OS flips dark mode.
    ThemeMode.SYSTEM -> if (AppSettings.systemDark) DarkPalette else LightPalette
    ThemeMode.AMOLED -> AmoledPalette
    ThemeMode.DARK -> DarkPalette
    ThemeMode.LIGHT -> LightPalette
}

/**
 * The palette in force.
 *
 * These used to be plain `val`s on a singleton. They are getters now because
 * the mode is Compose state ([AppSettings.themeMode]): reading a token inside a
 * composition subscribes that composable to theme changes, so flipping the
 * switch in Settings repaints the whole app — including canvas glyphs, which
 * re-read their colours in the draw phase — with no extra plumbing at the ~300
 * call sites.
 */
object TrackerColors {
    private val palette: Palette
        get() = paletteFor(AppSettings.themeMode)

    val isLight: Boolean get() = palette.isLight

    // Backgrounds
    val PureBlack: Color get() = palette.pureBlack
    val SurfaceDark: Color get() = palette.surfaceDark
    val SurfaceCard: Color get() = palette.surfaceCard
    val SurfaceElevated: Color get() = palette.surfaceElevated
    val SurfaceInput: Color get() = palette.surfaceInput

    // Borders
    val HairlineBorder: Color get() = palette.hairlineBorder
    val HairlineBorderLight: Color get() = palette.hairlineBorderLight
    val BorderFocused: Color get() = palette.borderFocused

    // Accent + text
    val PrimaryWhite: Color get() = palette.primaryWhite
    val PrimaryAccent: Color get() = palette.primaryAccent
    val TextPrimary: Color get() = palette.textPrimary
    val TextSecondary: Color get() = palette.textSecondary
    val TextMuted: Color get() = palette.textMuted
    val TextSubtle: Color get() = palette.textSubtle

    // Status
    val SafeEmerald: Color get() = palette.safeEmerald
    val SafeEmeraldSubtle: Color get() = palette.safeEmeraldSubtle
    val WarningAmber: Color get() = palette.warningAmber
    val WarningAmberSubtle: Color get() = palette.warningAmberSubtle
    val DangerRose: Color get() = palette.dangerRose
    val DangerRoseSubtle: Color get() = palette.dangerRoseSubtle
}

/** Material scheme mirroring the active palette, so Material widgets follow the theme. */
private fun trackerColorScheme(): ColorScheme =
    if (TrackerColors.isLight) {
        lightColorScheme(
            primary = TrackerColors.PrimaryWhite,
            onPrimary = TrackerColors.PureBlack,
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
            onError = TrackerColors.PureBlack,
            errorContainer = TrackerColors.DangerRoseSubtle,
            onErrorContainer = TrackerColors.DangerRose
        )
    } else {
        darkColorScheme(
            primary = TrackerColors.PrimaryWhite,
            onPrimary = TrackerColors.PureBlack,
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
            onError = TrackerColors.TextPrimary,
            errorContainer = TrackerColors.DangerRoseSubtle,
            onErrorContainer = TrackerColors.DangerRose
        )
    }

@Composable
fun TrackerTheme(content: @Composable () -> Unit) {
    // Keep the SYSTEM resolution in step with the OS. [AppSettings.primeSystemDark]
    // sets it before the first frame; this picks up any later change (system
    // theme toggle, configuration change) without restarting the app.
    val systemDark = systemIsDark()
    SideEffect { AppSettings.systemDark = systemDark }

    // Status/navigation bar icons have to flip with the theme: the app draws
    // edge to edge, so a light page under white system icons would vanish.
    ApplySystemBarStyle(isLight = TrackerColors.isLight)

    MaterialTheme(
        colorScheme = trackerColorScheme(),
        content = content
    )
}
