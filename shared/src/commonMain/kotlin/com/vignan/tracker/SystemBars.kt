package com.vignan.tracker

import androidx.compose.runtime.Composable

/**
 * Point the platform's status/navigation bar icons at the right contrast.
 *
 * The app draws edge to edge, so the bars are transparent and the app's own
 * page background shows through them: white icons over a light page are
 * invisible, and vice versa. Callers pass the active theme's lightness.
 */
@Composable
expect fun ApplySystemBarStyle(isLight: Boolean)
