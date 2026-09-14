package com.vignan.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.1f

        val path = Path().apply {
            moveTo(0f, h / 2f)
            quadraticTo(w / 2f, -h / 4f, w, h / 2f)
            quadraticTo(w / 2f, h + h / 4f, 0f, h / 2f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeW)
        )
        drawCircle(
            color = color,
            radius = w * 0.18f,
            center = Offset(w / 2f, h / 2f)
        )
    }
}

@Composable
private fun EyeOffIcon(modifier: Modifier = Modifier, color: Color) {
    Box(modifier = modifier.size(18.dp)) {
        EyeIcon(modifier = Modifier.fillMaxSize(), color = color.copy(alpha = 0.4f))
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = color,
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
                strokeWidth = size.width * 0.1f
            )
        }
    }
}
