package com.vignan.tracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Height of a plain settings row (label + trailing control).
 *
 * The theme row and the Amoled switch under it are a pair, so they share this
 * exact height: a 32dp switch row padded to its natural size next to a one-line
 * theme row read as two different weights of control.
 */
private val ROW_HEIGHT = 44.dp

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
    val registerPinned = AppSettings.registerTotalsPinned
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
            ThemeField(
                value = AppSettings.themeMode,
                onSelect = { mode -> scope.launch { AppSettings.setThemeMode(mode) } }
            )

            // The true-black variant is not a fourth look: it only exists while
            // something dark is showing (explicit Dark, or System on a dark
            // device), so it appears beneath the picker instead of in its list.
            AnimatedVisibility(
                visible = AppSettings.isDarkNow,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column {
                    SettingsDivider()

                    SettingsInlineToggleRow(
                        label = "Amoled dark",
                        checked = AppSettings.amoledDark,
                        onCheckedChange = { enabled ->
                            scope.launch { AppSettings.setAmoledDark(enabled) }
                        }
                    )
                }
            }
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

            SettingsDivider()

            // The app honours Android's "Remove animations" on its own; this is
            // an explicit override for people who want the interface still but
            // the movement gone, without changing a system-wide setting.
            SettingsToggleRow(
                label = "Interface motion",
                detail = if (AppSettings.motionEnabled) "Transitions and press feedback"
                else "Instant changes, no animation",
                checked = AppSettings.motionEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { AppSettings.setMotionEnabled(enabled) }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --------------------------------------------------------- register
        SettingsSection(title = "REGISTER") {
            SettingsToggleRow(
                label = "Pin totals",
                detail = if (registerPinned) "ATTED/HELD and % stay put"
                else "ATTED/HELD and % trail the last day",
                checked = registerPinned,
                onCheckedChange = { enabled ->
                    scope.launch { AppSettings.setRegisterTotalsPinned(enabled) }
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
            SettingsInfoRow(
                label = "App version",
                value = "$version · ${AppDistribution.label}"
            )
            SettingsDivider()
            SettingsInfoRow(label = "Maintained by", value = "XD")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --------------------------------------------------------- account
        SettingsSection(title = "ACCOUNT") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressClickable(pressedScale = 0.99f) { showSignOutConfirm = true }
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
 * "Theme" row: the current look named on the right, tapping opens the same
 * picker window the attendance month field uses. The row deliberately carries
 * no box of its own — the section card already draws the outline, and a second
 * one inside it read as a nested frame — so only the expanded state tints it.
 */
@Composable
private fun ThemeField(
    value: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val caretRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "themeCaretRotation"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val menuWidth = maxWidth

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .background(
                    if (expanded) TrackerColors.SurfaceElevated else Color.Transparent
                )
                .pressClickable(pressedScale = 0.99f) { expanded = !expanded }
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Theme",
                color = TrackerColors.TextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value.label,
                color = TrackerColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (expanded) TrackerColors.SurfaceInput else Color.Transparent
                    )
                    .graphicsLayer { rotationZ = caretRotation },
                contentAlignment = Alignment.Center
            ) {
                CaretDown(
                    tint = if (expanded) TrackerColors.TextSecondary
                    else TrackerColors.TextMuted
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = TrackerColors.SurfaceElevated,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, TrackerColors.HairlineBorderLight),
            offset = DpOffset(0.dp, 0.dp),
            modifier = Modifier.width(menuWidth)
        ) {
            Column(modifier = Modifier.heightIn(max = 300.dp)) {
                ThemeMenuEntry(
                    label = ThemeMode.LIGHT.label,
                    isSelected = value == ThemeMode.LIGHT,
                    onClick = { onSelect(ThemeMode.LIGHT); expanded = false }
                )
                ThemeMenuEntry(
                    label = ThemeMode.DARK.label,
                    isSelected = value == ThemeMode.DARK,
                    onClick = { onSelect(ThemeMode.DARK); expanded = false }
                )

                ThemeMenuEntry(
                    label = ThemeMode.SYSTEM.label,
                    detail = "Follows the device · currently " +
                        (if (AppSettings.systemDark) "Dark" else "Light"),
                    isSelected = value == ThemeMode.SYSTEM,
                    onClick = { onSelect(ThemeMode.SYSTEM); expanded = false }
                )
            }
        }
    }
}

/** One line in the theme picker window, styled like the month picker's. */
@Composable
private fun ThemeMenuEntry(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    detail: String? = null,
    modifier: Modifier = Modifier
) {
    DropdownMenuItem(
        modifier = modifier,
        text = {
            Column {
                Text(
                    text = label,
                    color = if (isSelected) TrackerColors.PrimaryWhite
                    else TrackerColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        color = TrackerColors.TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        onClick = onClick
    )
}

/** Small rounded chevron used as the dropdown affordance. */
@Composable
private fun CaretDown(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(9.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.30f)
            lineTo(w * 0.5f, h * 0.70f)
            lineTo(w * 0.88f, h * 0.30f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = w * 0.18f, cap = StrokeCap.Round)
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
            colors = trackerSwitchColors()
        )
    }
}

/**
 * One-line switch row at [ROW_HEIGHT], for switches that belong to the row
 * above them rather than to their own labelled block.
 */
@Composable
private fun SettingsInlineToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .padding(start = 16.dp, end = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TrackerColors.TextPrimary,
            fontSize = 14.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = trackerSwitchColors()
        )
    }
}

/** The app's switch styling: ink track when on, hairline outline when off. */
@Composable
private fun trackerSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = TrackerColors.PureBlack,
    checkedTrackColor = TrackerColors.PrimaryWhite,
    checkedBorderColor = TrackerColors.PrimaryWhite,
    uncheckedThumbColor = TrackerColors.TextMuted,
    uncheckedTrackColor = TrackerColors.SurfaceElevated,
    uncheckedBorderColor = TrackerColors.HairlineBorderLight
)

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
