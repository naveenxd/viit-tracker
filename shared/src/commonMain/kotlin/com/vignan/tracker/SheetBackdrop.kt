package com.vignan.tracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Scrim behind every sheet.
 *
 * Material's default is black at 32%, which over this app's near-black page
 * barely registers as a change: the sheet ends up looking pasted on rather than
 * raised over something. This is dark enough that the page behind is visibly
 * put back a layer.
 */
internal val SheetScrim = Color.Black.copy(alpha = 0.5f)

/**
 * Blur the content behind a sheet.
 *
 * A sheet is a dialog, so on Android 12+ the system can blur what sits behind
 * the dialog's own window — the app content it covers — which is the backdrop
 * Material's scrim alone cannot fake. Below 12, and on the devices whose GPU
 * cannot afford it, the scrim has to do the separating on its own.
 */
@Composable
internal expect fun SheetBackdropBlur()
