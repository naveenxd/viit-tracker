package com.vignan.tracker

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView

@Composable
actual fun systemIsDark(): Boolean {
    val uiMode = LocalConfiguration.current.uiMode
    return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

@Composable
actual fun ApplySystemBarStyle(isLight: Boolean) {
    val view = LocalView.current
    val pageBackground = TrackerColors.PureBlack

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect

        applyBarIconContrast(window, view, isLight)

        // The window background is what fills the bar strips and the gap the
        // splash leaves behind. Keep it on the page colour so a light theme
        // never shows a black frame around itself.
        window.setBackgroundDrawable(ColorDrawable(pageBackground.toArgb()))
    }
}

/**
 * Flip the status/navigation bar icons.
 *
 * Done with platform APIs on purpose: the app's edge-to-edge bars are
 * transparent, so the icons sit on the app's own page background and have to
 * follow the theme the user picked — which `enableEdgeToEdge` cannot know,
 * since it only reads the *system* dark-mode setting.
 *
 * Below API 30 there is no WindowInsetsController, only the deprecated
 * `systemUiVisibility` flags, so the whole fallback is suppressed rather than
 * every line of it.
 */
@Suppress("DEPRECATION")
private fun applyBarIconContrast(window: android.view.Window, view: View, isLight: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = window.insetsController ?: return
        val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        controller.setSystemBarsAppearance(if (isLight) lightBars else 0, lightBars)
        return
    }

    var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    view.systemUiVisibility = if (isLight) {
        view.systemUiVisibility or flags
    } else {
        view.systemUiVisibility and flags.inv()
    }
}
