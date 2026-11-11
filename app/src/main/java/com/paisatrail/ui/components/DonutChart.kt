package com.paisatrail.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

data class ChartSlice(val value: Float, val color: Color)

/** Degrees of empty space left between neighbouring slices. */
private const val SLICE_GAP_DEGREES = 2.2f

/**
 * Canvas-drawn donut chart (no chart library).
 *
 * Whenever the data changes the ring sweeps in clockwise from the top while turning a quarter
 * of a circle into its resting position.
 *
 * @param holeFraction 0 draws a full pie, 0.6 a ring whose hole is 60% of the radius.
 * @param emptyColor ring colour used when there is nothing to plot.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    emptyColor: Color,
    modifier: Modifier = Modifier,
    holeFraction: Float = 0.56f
) {
    val reduceMotion = LocalReduceMotion.current
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(slices, reduceMotion) {
        if (reduceMotion) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
    }

    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height)
        if (diameter <= 0f) return@Canvas
        val ringWidth = diameter / 2f * (1f - holeFraction.coerceIn(0f, 0.95f))
        val arcDiameter = diameter - ringWidth
        val topLeft = Offset((size.width - arcDiameter) / 2f, (size.height - arcDiameter) / 2f)
        val arcSize = Size(arcDiameter, arcDiameter)
        val stroke = Stroke(width = ringWidth)

        // A faint full ring sits underneath, so the chart never looks broken mid-animation.
        drawArc(emptyColor, 0f, 360f, false, topLeft, arcSize, style = stroke)

        val visible = slices.filter { it.value > 0f }
        val total = visible.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return@Canvas

        val progress = reveal.value.coerceIn(0f, 1f)
        val gap = if (visible.size > 1) SLICE_GAP_DEGREES else 0f
        var startAngle = -90f - 90f * (1f - progress)
        for (slice in visible) {
            val fullSweep = slice.value / total * 360f
            val sweep = (fullSweep * progress - gap).coerceAtLeast(0.2f)
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            startAngle += fullSweep * progress
        }
    }
}
