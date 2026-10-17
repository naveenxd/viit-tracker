package com.vignan.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The one header every bottom-nav tab renders.
 *
 * All five tabs used to roll their own: titles at 16sp vs 22sp, eyebrow
 * tracking at 1.2sp vs 2sp, some with a top inset, some without, and Profile
 * with no header at all — so switching tabs made the whole page jump. This
 * fixes the rhythm in one place:
 *
 *   EYEBROW   monospace 10sp bold, 2sp tracking, muted
 *   Title     sans 17sp bold
 *   subtitle  monospace 11sp subtle
 *
 * with the same 8dp top / 2dp bottom breathing room, and the settings gear
 * pinned to the far right on every tab. Callers always pass a [subtitle] (even
 * a short one) so headers keep an identical height and nothing shifts on tab
 * change.
 */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleSuffix: String? = null,
    onSettingsClick: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
    action: @Composable () -> Unit = {
        HeaderIconButton(onClick = onSettingsClick, contentDescription = "Settings") { SettingsGlyph() }
    }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                color = TrackerColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = TrackerColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!titleSuffix.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = titleSuffix,
                        color = TrackerColors.TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            trailing?.invoke()
            action()
        }
    }
}

/** Circular hairline button used for the header's trailing gear / close affordance. */
@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .semantics { this.contentDescription = contentDescription ?: "" },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Gear: ring body, eight radial teeth, hollow hub. Matches the nav bar's canvas glyph weight. */
@Composable
fun SettingsGlyph(
    modifier: Modifier = Modifier,
    color: Color = TrackerColors.TextSecondary
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val sw = 1.5.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Teeth stay short relative to the body — long ones read as a sun.
        val bodyRadius = w * 0.34f
        val toothInner = bodyRadius - sw * 0.4f
        val toothOuter = w * 0.45f

        repeat(8) { i ->
            val angle = PI / 4.0 * i + PI / 8.0
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                color = color,
                start = Offset(center.x + dx * toothInner, center.y + dy * toothInner),
                end = Offset(center.x + dx * toothOuter, center.y + dy * toothOuter),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }

        drawCircle(color = color, radius = bodyRadius, center = center, style = stroke)
        drawCircle(color = color, radius = w * 0.085f, center = center, style = stroke)
    }
}

/** Minimal "×" for closing the settings page. */
@Composable
fun CloseGlyph(
    modifier: Modifier = Modifier,
    color: Color = TrackerColors.TextSecondary
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val sw = 1.6.dp.toPx()
        val a = size.width * 0.30f
        val b = size.width * 0.70f
        drawLine(color, Offset(a, a), Offset(b, b), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(b, a), Offset(a, b), strokeWidth = sw, cap = StrokeCap.Round)
    }
}
