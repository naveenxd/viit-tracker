package com.vignan.tracker

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The app's motion vocabulary, in one place.
 *
 * Everything here is deliberately restrained: short fades, a few percent of
 * scale, single-digit-percent slides. Motion in this app is meant to explain
 * *where a thing came from*, never to perform.
 *
 * ## Two ways motion gets switched off
 *
 * 1. **The system setting.** Compose already reads Android's animator duration
 *    scale and applies it to every animation clock, so "Remove animations" in
 *    Developer options / Accessibility already freezes everything below — no
 *    code needed for that.
 * 2. **The in-app switch** ([AppSettings.motionEnabled]). The helpers here
 *    collapse their specs to zero duration when it is off, which also covers
 *    the cases the system scale cannot: delay-only choreography and the
 *    press-scale feedback.
 *
 * Read [AppSettings.motionEnabled] through these helpers rather than directly,
 * so a single toggle governs the whole app.
 */

/** Named durations, so no call site invents its own number. */
object Motion {
    /** Icon/colour response. Short enough to feel immediate. */
    const val Fast = 90

    /** Default enter/exit for content swaps. */
    const val Base = 160

    /** Larger reveals: a full card or a page block arriving. */
    const val Medium = 220

    /** Screen-level pushes (settings over tabs). */
    const val Slow = 320

    /** Data-driven fills, e.g. the attendance progress bar. */
    const val Progress = 600

    /** Gap between consecutive items in a staggered list reveal. */
    const val StaggerStep = 40

    /**
     * Items past this index appear together with the last staggered one. A
     * long list would otherwise keep animating after the user is already
     * scrolling, which reads as lag rather than polish.
     */
    const val StaggerMax = 6
}

/** Shared easing for every tween, so accelerations match across the app. */
val MotionEasing: Easing = FastOutSlowInEasing

/**
 * A [tween] that collapses to zero duration when motion is switched off.
 *
 * Generic because the same spec type is needed for `Float` (fades, scale,
 * alpha) and `IntOffset` (slides).
 */
fun <T> motionTween(
    durationMillis: Int = Motion.Base,
    delayMillis: Int = 0,
    easing: Easing = MotionEasing
): TweenSpec<T> {
    val on = AppSettings.motionEnabled
    return tween(
        durationMillis = if (on) durationMillis else 0,
        delayMillis = if (on) delayMillis else 0,
        easing = easing
    )
}

/**
 * A [spring] that becomes a [snap] when motion is switched off.
 *
 * Springs ignore duration, so they cannot be collapsed by handing them a zero
 * length — the only honest "off" is to stop being a spring at all.
 */
fun <T> motionSpring(
    dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = Spring.StiffnessMediumLow
): AnimationSpec<T> =
    if (AppSettings.motionEnabled) {
        spring(dampingRatio = dampingRatio, stiffness = stiffness)
    } else {
        snap()
    }

// ------------------------------------------------------------------ transitions

/**
 * Default content swap: the new thing fades up in while the old one leaves
 * faster, so the two never read as a single muddy cross-dissolve.
 */
fun contentSwap(): ContentTransform =
    fadeIn(motionTween<Float>(Motion.Medium, delayMillis = Motion.Fast / 2))
        .togetherWith(fadeOut(motionTween<Float>(Motion.Fast)))

/**
 * Directional content swap for sibling pages (tabs, days, months).
 *
 * [fraction] is how far the incoming content travels, as a share of its own
 * width. Keep it small — 0.06–0.18 — because the point is to imply direction,
 * not to slide a whole screen across.
 */
fun horizontalSwap(forward: Boolean, fraction: Float = 0.08f): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        fadeIn(motionTween<Float>(Motion.Medium)) +
            slideInHorizontally(motionTween<IntOffset>(Motion.Medium)) {
                (it * fraction * direction).roundToInt()
            }
        ).togetherWith(
        fadeOut(motionTween<Float>(Motion.Fast)) +
            slideOutHorizontally(motionTween<IntOffset>(Motion.Base)) {
                (-it * fraction * direction).roundToInt()
            }
    )
}

// ------------------------------------------------------------------ modifiers

/**
 * Press feedback for a surface that draws its own container (a card, a pill, a
 * filled button): a small inward scale while the finger is down.
 *
 * Pass the same [interactionSource] the `clickable` already uses. Sites that
 * build their own source can use [pressClickable] instead.
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && AppSettings.motionEnabled) pressedScale else 1f,
        animationSpec = motionSpring<Float>(dampingRatio = 0.72f, stiffness = 900f),
        label = "pressFeedback"
    )

    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * `clickable` + [pressFeedback] in one, for surfaces that do not already own an
 * interaction source.
 *
 * Indication is suppressed deliberately: this app never used the Material
 * ripple, and adding one now would be a louder change than the scale it
 * accompanies.
 */
@Composable
fun Modifier.pressClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return pressFeedback(
        interactionSource = interactionSource,
        enabled = enabled,
        pressedScale = pressedScale
    ).clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * Entrance for a dialog's own content: a short fade with a 3% scale-up from
 * slightly smaller.
 *
 * Entrance only, deliberately. A dialog is added and removed by the caller's
 * `if (showing)`, so the window closes the instant the flag flips and there is
 * no frame left in which to play an exit.
 */
@Composable
fun Modifier.dialogEnter(): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = motionTween<Float>(Motion.Base),
        label = "dialogEnter"
    )

    return graphicsLayer {
        alpha = progress
        val scale = 0.97f + 0.03f * progress
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Alpha for a shimmering placeholder, pulsing between [initial] and [target].
 *
 * Holds at the midpoint when motion is off, so a loading skeleton still reads as
 * "placeholder" without a pulse. The one animation here that is not a tween of
 * another value, hence its own helper rather than a [motionTween] call site.
 */
@Composable
fun rememberShimmerAlpha(
    initial: Float = 0.30f,
    target: Float = 0.70f,
    durationMillis: Int = 900
): Float {
    if (!AppSettings.motionEnabled) return (initial + target) / 2f

    val transition = rememberInfiniteTransition(label = "shimmer")
    val pulse by transition.animateFloat(
        initialValue = initial,
        targetValue = target,
        animationSpec = infiniteRepeatable(tween(durationMillis), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    return pulse
}

/**
 * Fade-and-lift entrance for list content.
 *
 * [index] is the item's position in its list. Everything past [Motion.StaggerMax]
 * shares the last slot's delay, so a 40-row register never takes 1.6s to finish
 * appearing.
 *
 * Applied per item, this replays whenever the item is composed — including when
 * a lazy list scrolls it back into view. That is why the stagger is capped: the
 * visible payoff is at the top of a list, and the tail should be inert.
 */
@Composable
fun Modifier.staggeredAppear(index: Int): Modifier {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = motionTween<Float>(
            durationMillis = Motion.Medium,
            delayMillis = index.coerceAtMost(Motion.StaggerMax) * Motion.StaggerStep
        ),
        label = "staggeredAppear"
    )

    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 10.dp.toPx()
    }
}
