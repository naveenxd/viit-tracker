package com.vignan.tracker

import androidx.compose.runtime.Composable

/**
 * System back-press handling that works from `commonMain`.
 *
 * Needed by the in-place settings page: Android's system back would otherwise
 * finish the activity and drop the user out of the app instead of returning to
 * the tab they came from.
 */
@Composable
expect fun AppBackHandler(enabled: Boolean = true, onBack: () -> Unit)
