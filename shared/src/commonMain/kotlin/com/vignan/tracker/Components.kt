package com.vignan.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

enum class SubjectFilter {
    ALL, AT_RISK, SAFE
}

sealed interface AttendanceInsight {
    data class Safe(val canSkipClasses: Int) : AttendanceInsight
    data class AtRisk(val requiredClasses: Int) : AttendanceInsight
}

fun calculateMargin(attended: Int, conducted: Int): AttendanceInsight {
    if (conducted <= 0) return AttendanceInsight.Safe(0)
    val pct = (attended.toDouble() / conducted) * 100.0
    return if (pct >= 75.0) {
        val canSkip = floor((4.0 * attended - 3.0 * conducted) / 3.0).toInt()
        AttendanceInsight.Safe(maxOf(0, canSkip))
    } else {
        val mustAttend = ceil(3.0 * conducted - 4.0 * attended).toInt()
        AttendanceInsight.AtRisk(maxOf(1, mustAttend))
    }
}

@Composable
fun AnimatedEyeIcon(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = TrackerColors.TextSecondary
) {
    AnimatedContent(
        targetState = isVisible,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "EyeIconAnimation"
    ) { visible ->
        if (visible) {
            EyeIcon(modifier = modifier, color = color)
        } else {
            EyeOffIcon(modifier = modifier, color = color)
        }
    }
}

@Composable
private fun EyeIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.11f
        val strokeStyle = Stroke(width = strokeW, cap = StrokeCap.Round)

        // Eyelid top curve (arch)
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.52f)
            quadraticTo(w * 0.5f, h * 0.22f, w * 0.88f, h * 0.52f)
        }
        drawPath(path = path, color = color, style = strokeStyle)

        // 3 Top Eyelashes radiating outwards
        drawLine(
            color = color,
            start = Offset(w * 0.26f, h * 0.38f),
            end = Offset(w * 0.16f, h * 0.20f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.32f),
            end = Offset(w * 0.5f, h * 0.12f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.74f, h * 0.38f),
            end = Offset(w * 0.84f, h * 0.20f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        // Solid pupil circle hanging right underneath
        drawCircle(
            color = color,
            radius = w * 0.16f,
            center = Offset(w * 0.5f, h * 0.62f)
        )
    }
}

@Composable
private fun EyeOffIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.11f
        val strokeStyle = Stroke(width = strokeW, cap = StrokeCap.Round)

        // Closed Eyelid curve (arch)
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.42f)
            quadraticTo(w * 0.5f, h * 0.68f, w * 0.88f, h * 0.42f)
        }
        drawPath(path = path, color = color, style = strokeStyle)

        // 5 Bottom Eyelashes radiating downwards
        drawLine(
            color = color,
            start = Offset(w * 0.18f, h * 0.46f),
            end = Offset(w * 0.10f, h * 0.64f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.34f, h * 0.56f),
            end = Offset(w * 0.28f, h * 0.76f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.60f),
            end = Offset(w * 0.5f, h * 0.82f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.66f, h * 0.56f),
            end = Offset(w * 0.72f, h * 0.76f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.82f, h * 0.46f),
            end = Offset(w * 0.90f, h * 0.64f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}
