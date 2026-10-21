package com.vignan.tracker.update

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignan.tracker.AppVersion
import com.vignan.tracker.BuildConfig
import com.vignan.tracker.TrackerColors

private const val PREFS = "viit_update_prefs"
private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

/**
 * Shows the embedded changelog once, on the first launch after the installed
 * version name changes. The first run of a build that ships this feature stores
 * a silent baseline; debug builds never show it.
 */
@Composable
fun WhatsNewOverlay(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    }

    var shownVersion by remember { mutableStateOf<String?>(null) }
    var bullets by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(prefs) {
        if (BuildConfig.DEBUG) return@LaunchedEffect

        val current = AppVersion.name(context)
        val lastSeen = prefs.getString(KEY_LAST_SEEN_VERSION, null)

        if (lastSeen == current) return@LaunchedEffect

        if (lastSeen == null && AppVersion.isFreshInstall(context)) {
            // Clean install of the first build with this sheet: remember the
            // baseline so only a later update interrupts the user.
            prefs.edit().putString(KEY_LAST_SEEN_VERSION, current).apply()
            return@LaunchedEffect
        }

        // Existing install updating into the first build with this sheet has
        // no baseline yet (lastSeen == null), but is not a fresh install, so it
        // falls through and shows the current changelog.

        val items = BuildConfig.CHANGELOG
            .lineSequence()
            .map { it.trim().removePrefix("* ").removePrefix("- ").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        if (items.isEmpty()) {
            prefs.edit().putString(KEY_LAST_SEEN_VERSION, current).apply()
            return@LaunchedEffect
        }

        bullets = items
        shownVersion = current
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        shownVersion?.let { version ->
            WhatsNewSheet(
                version = version,
                bullets = bullets,
                onDismiss = {
                    prefs.edit().putString(KEY_LAST_SEEN_VERSION, version).apply()
                    shownVersion = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsNewSheet(
    version: String,
    bullets: List<String>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        contentColor = TrackerColors.TextPrimary,
        dragHandle = null,
        scrimColor = TrackerColors.PureBlack.copy(alpha = 0.66f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Surface(
            color = TrackerColors.SurfaceCard,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp)
                    .navigationBarsPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp)
                        .background(
                            TrackerColors.HairlineBorderLight,
                            RoundedCornerShape(50),
                        ),
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    rememberWhatsNewIcon()?.let { icon ->
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "What's new in v${version.removePrefix("v")}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TrackerColors.TextPrimary,
                        )
                        Text(
                            text = "Here's what changed in this update.",
                            fontSize = 12.sp,
                            color = TrackerColors.TextMuted,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "CHANGELOG",
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    color = TrackerColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    bullets.forEach { item -> WhatsNewBullet(item) }
                }

                Spacer(Modifier.height(20.dp))

                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    color = TrackerColors.PrimaryWhite,
                    contentColor = TrackerColors.PureBlack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Got it",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsNewBullet(item: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .background(TrackerColors.PrimaryWhite, CircleShape),
        )
        Text(
            text = item,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = TrackerColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun rememberWhatsNewIcon(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 192, 192)
            drawable.draw(Canvas(bitmap))
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}
