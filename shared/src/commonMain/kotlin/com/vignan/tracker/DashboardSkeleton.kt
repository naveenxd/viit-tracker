package com.vignan.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * First-load placeholder for the home tab.
 *
 * Mirrors the real page's rhythm — header, hero number, two stat panels, fetch
 * button, today card, mark buttons and projection card — so the data reveal
 * lands in the same layout instead of jumping from a spinner to a full page.
 * Shows only while the stored session is being checked or the first snapshot
 * is still in flight; cached snapshots skip it.
 */
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberShimmerAlpha()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TrackerColors.PureBlack)
            .semantics { contentDescription = "Loading attendance" },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 86.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SkeletonHeader(alpha) }
        item { SkeletonHero(alpha) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonPanel(Modifier.weight(0.6f).height(112.dp)) {
                    SkeletonLine(alpha, Modifier.fillMaxWidth(0.62f), 10.dp)
                    SkeletonLine(alpha, Modifier.fillMaxWidth(0.86f), 10.dp)
                    SkeletonLine(alpha, Modifier.fillMaxWidth(0.44f), 10.dp)
                }
                SkeletonPanel(Modifier.weight(0.4f).height(112.dp)) {
                    SkeletonLine(alpha, Modifier.fillMaxWidth(0.70f), 10.dp)
                    SkeletonLine(alpha, Modifier.fillMaxWidth(0.48f), 22.dp)
                    SkeletonLine(alpha, Modifier.fillMaxWidth(0.82f), 9.dp)
                }
            }
        }

        item {
            SkeletonButton(
                alpha = alpha,
                modifier = Modifier.fillMaxWidth(),
                lineWidth = 118.dp,
                contentAlignment = Alignment.CenterStart,
            )
        }

        item {
            SkeletonPanel(Modifier.fillMaxWidth()) {
                SkeletonLine(
                    alpha,
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(152.dp),
                    11.dp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    repeat(7) {
                        SkeletonLine(
                            alpha,
                            Modifier.weight(1f),
                            30.dp,
                            RoundedCornerShape(7.dp),
                        )
                    }
                }
                SkeletonLine(
                    alpha,
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(120.dp),
                    8.dp,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(2) {
                    SkeletonButton(
                        alpha = alpha,
                        modifier = Modifier.weight(1f),
                        lineWidth = 62.dp,
                    )
                }
            }
        }

        item {
            SkeletonPanel(Modifier.fillMaxWidth().height(132.dp)) {
                SkeletonLine(alpha, Modifier.fillMaxWidth(0.30f), 9.dp)
                SkeletonLine(alpha, Modifier.fillMaxWidth(0.56f), 12.dp)
                SkeletonLine(alpha, Modifier.fillMaxWidth(0.82f), 12.dp)
                SkeletonLine(alpha, Modifier.fillMaxWidth(0.44f), 9.dp)
            }
        }
    }
}

@Composable
private fun SkeletonHeader(alpha: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(alpha, Modifier.width(52.dp), 8.dp)
            SkeletonLine(alpha, Modifier.width(178.dp), 22.dp, RoundedCornerShape(6.dp))
            SkeletonLine(alpha, Modifier.width(124.dp), 9.dp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkeletonLine(alpha, Modifier.width(52.dp), 24.dp)
            SkeletonLine(alpha, Modifier.size(34.dp), 34.dp)
        }
    }
}

@Composable
private fun SkeletonHero(alpha: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            SkeletonLine(alpha, Modifier.width(140.dp), 52.dp, RoundedCornerShape(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SkeletonLine(alpha, Modifier.width(70.dp), 14.dp)
                SkeletonLine(alpha, Modifier.width(52.dp), 9.dp)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TrackerColors.HairlineBorderLight),
        )
    }
}

@Composable
private fun SkeletonPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SkeletonButton(
    alpha: Float,
    modifier: Modifier = Modifier,
    lineWidth: Dp = 100.dp,
    contentAlignment: Alignment = Alignment.Center,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = contentAlignment,
    ) {
        SkeletonLine(alpha, Modifier.width(lineWidth), 9.dp)
    }
}

@Composable
private fun SkeletonLine(
    alpha: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(TrackerColors.SurfaceElevated.copy(alpha = alpha)),
    )
}
