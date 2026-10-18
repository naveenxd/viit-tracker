package com.vignan.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Full-screen settings page, reachable from the gear in every tab's header.
 *
 * Rendered in place by [DashboardScreen] rather than pushed onto a navigation
 * stack — the app has none — so closing is an explicit "×"/system-back rather
 * than a route pop. The bottom nav is hidden while it is open.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }
    val use24Hour = AppSettings.use24HourClock
    val version = remember { getPlatform().appVersion }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TrackerColors.PureBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Deliberately NOT the shared ScreenHeader: its eyebrow/title/subtitle
        // triple is navigation chrome ("SETTINGS / Preferences / Display · app ·
        // account"), which on a page you just tapped into only restates itself
        // and reads as clutter. Instead: one large-title line with the close
        // button sitting where the gear you tapped was, and the sections below
        // label themselves.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                color = TrackerColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.6).sp,
                modifier = Modifier.weight(1f)
            )

            HeaderIconButton(
                onClick = onBack,
                contentDescription = "Close settings"
            ) { CloseGlyph() }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ------------------------------------------------------------ theme
        SettingsSection(title = "THEME") {
            ThemeModeSelector(
                selected = AppSettings.themeMode,
                onSelect = { mode -> scope.launch { AppSettings.setThemeMode(mode) } }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---------------------------------------------------------- display
        SettingsSection(title = "DISPLAY") {
            SettingsToggleRow(
                label = "24-hour time",
                detail = if (use24Hour) "08:45–09:35" else "8.45–9.35",
                checked = use24Hour,
                onCheckedChange = { enabled ->
                    scope.launch { AppSettings.setUse24HourClock(enabled) }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ------------------------------------------------------- upcoming
        SettingsSection(title = "MORE") {
            SettingsSoonRow(label = "Notifications", detail = "Class and attendance alerts")
            SettingsDivider()
            SettingsSoonRow(label = "Widget layout", detail = "Choose what the home-screen widget shows")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---------------------------------------------------------- about
        SettingsSection(title = "ABOUT") {
            SettingsInfoRow(label = "App version", value = version)
            SettingsDivider()
            SettingsInfoRow(label = "Maintained by", value = "XD")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --------------------------------------------------------- account
        SettingsSection(title = "ACCOUNT") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSignOutConfirm = true }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sign out",
                    color = TrackerColors.DangerRose,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showSignOutConfirm) {
        SignOutConfirmDialog(
            onDismiss = { showSignOutConfirm = false },
            onConfirm = {
                showSignOutConfirm = false
                onLogout()
            }
        )
    }
}

// ---------------------------------------------------------------- building blocks

/**
 * Three-way theme picker. Each segment previews the mode it selects — page
 * colour behind, card colour in front — so the choice is legible before it is
 * made, and the control re-colours itself the instant it is applied.
 */
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val swatch = themeSwatch(mode)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) TrackerColors.SurfaceElevated else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (isSelected) TrackerColors.HairlineBorderLight else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .selectable(
                        selected = isSelected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton
                    ) { onSelect(mode) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ThemeSwatchBadge(swatch)

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text = mode.label,
                    color = if (isSelected) TrackerColors.PrimaryWhite else TrackerColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** Miniature of a theme: the page colour with a card resting on it. */
@Composable
private fun ThemeSwatchBadge(swatch: ThemeSwatch) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(swatch.page)
            .border(1.dp, swatch.hairline, RoundedCornerShape(5.dp))
            .padding(2.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(swatch.card)
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TrackerColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(TrackerColors.HairlineBorder)
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TrackerColors.TextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                color = TrackerColors.TextSubtle,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TrackerColors.PureBlack,
                checkedTrackColor = TrackerColors.PrimaryWhite,
                checkedBorderColor = TrackerColors.PrimaryWhite,
                uncheckedThumbColor = TrackerColors.TextMuted,
                uncheckedTrackColor = TrackerColors.SurfaceElevated,
                uncheckedBorderColor = TrackerColors.HairlineBorderLight
            )
        )
    }
}

/** A row that exists to say "not yet" — visibly inert, never tappable. */
@Composable
private fun SettingsSoonRow(label: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.45f)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TrackerColors.TextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                color = TrackerColors.TextSubtle,
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(TrackerColors.SurfaceElevated)
                .border(1.dp, TrackerColors.HairlineBorderLight, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "SOON",
                color = TrackerColors.TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TrackerColors.TextSecondary,
            fontSize = 14.sp,
            fontFamily = FontFamily.SansSerif
        )
        Text(
            text = value,
            color = TrackerColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
