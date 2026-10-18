package com.vignan.tracker

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Blur-behind radius, in dp. The framework wants pixels, so it is scaled by the
 * display density at the call site rather than baked in. Kept modest: the ramp
 * past about 20dp buys little and the sampling cost climbs with it.
 */
private const val BACKDROP_BLUR_DP = 18

/**
 * The dialog window [this] view lives in, or null when it is in the activity
 * window instead.
 *
 * A sheet's content is hosted by the sheet's own view, which is the window
 * provider, so walking up finds it without reaching for the activity — whose
 * window is the wrong thing to blur, since it is the content being blurred.
 */
private fun View.dialogWindow(): Window? {
    var current: View? = this
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent as? View
    }
    return null
}

/**
 * Take the app behind the dialog out of focus.
 *
 * "Blur behind" is the cross-window effect: it blurs everything drawn under the
 * window, which for a sheet is the screen it was opened over. It is what the
 * dialog's own background blur cannot do — that one frosts the window against
 * its own content, and the sheet already paints a solid container over itself.
 *
 * Two platform conditions apply, both of which the scrim covers for: the API
 * exists only from Android 12, and the system may switch cross-window blur off
 * at runtime (battery saver, low-end GPUs). Hence blur *and* dim rather than
 * blur instead of dim.
 */
@Composable
internal actual fun SheetBackdropBlur() {
    val view = LocalView.current

    SideEffect {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@SideEffect

        val window = view.dialogWindow() ?: return@SideEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)

        val radius = (BACKDROP_BLUR_DP * view.resources.displayMetrics.density).toInt()
        val params = window.attributes
        @Suppress("DEPRECATION") // The API it points at is blur *background*, not this effect.
        params.setBlurBehindRadius(radius)
        window.attributes = params
    }
}
