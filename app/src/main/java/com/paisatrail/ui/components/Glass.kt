package com.paisatrail.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The "glass" look, built from three ingredients that work on every Android version:
 *
 *  1. AuroraBackground: large, soft, slowly drifting pools of colour behind the whole app.
 *  2. Modifier.glass: a see-through pane with a light-to-dark sheen and a bright rim on top,
 *     so the colour behind glows through cards the way it does through frosted glass.
 *  3. A "strong" variant for bars and sheets that sit over text, tinted enough to stay readable.
 *
 * There is no live blur of the content behind a pane. That needs a blur library or API 31+ and
 * can be layered onto Modifier.glass later without touching any screen.
 */

/** True when the current Material colour scheme is the dark one. */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

private class GlassColors(val fillTop: Color, val fillBottom: Color, val rimTop: Color, val rimBottom: Color)

@Composable
private fun glassColors(strong: Boolean): GlassColors {
    val surface = MaterialTheme.colorScheme.surface
    return if (isDarkTheme()) {
        if (strong) {
            GlassColors(
                fillTop = surface.copy(alpha = 0.86f),
                fillBottom = surface.copy(alpha = 0.78f),
                rimTop = Color.White.copy(alpha = 0.26f),
                rimBottom = Color.White.copy(alpha = 0.05f)
            )
        } else {
            GlassColors(
                fillTop = Color.White.copy(alpha = 0.115f),
                fillBottom = Color.White.copy(alpha = 0.045f),
                rimTop = Color.White.copy(alpha = 0.30f),
                rimBottom = Color.White.copy(alpha = 0.05f)
            )
        }
    } else {
        if (strong) {
            GlassColors(
                fillTop = Color.White.copy(alpha = 0.94f),
                fillBottom = Color.White.copy(alpha = 0.86f),
                rimTop = Color.White,
                rimBottom = Color.Black.copy(alpha = 0.06f)
            )
        } else {
            GlassColors(
                fillTop = Color.White.copy(alpha = 0.80f),
                fillBottom = Color.White.copy(alpha = 0.56f),
                rimTop = Color.White,
                rimBottom = Color.Black.copy(alpha = 0.05f)
            )
        }
    }
}

/**
 * Turns the element into a pane of glass: clipped to [shape], filled with a translucent sheen
 * and outlined by a rim that is bright at the top and fades towards the bottom.
 *
 * @param strong use for surfaces that overlap text (tab bar, bottom sheets, sticky footers).
 */
@Composable
fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier {
    val colors = glassColors(strong)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(colors.fillTop, colors.fillBottom)), shape)
        .border(1.dp, Brush.verticalGradient(listOf(colors.rimTop, colors.rimBottom)), shape)
}

/** A glass pane with a wash of [tint] over it, for selected or highlighted elements. */
@Composable
fun Modifier.tintedGlass(shape: Shape, tint: Color): Modifier {
    val rimTop = Color.White.copy(alpha = if (isDarkTheme()) 0.35f else 0.9f)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.34f), tint.copy(alpha = 0.18f))), shape)
        .border(1.dp, Brush.verticalGradient(listOf(rimTop, tint.copy(alpha = 0.25f))), shape)
}

/**
 * Full-screen backdrop: the theme background with four big, soft colour pools that drift on a
 * slow loop. Everything else in the app is drawn on top of this, so glass panes pick up its glow.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    val dark = isDarkTheme()
    val strength = if (dark) 0.40f else 0.30f
    val pools = if (dark) {
        listOf(Color(0xFF17B26A), Color(0xFF3D5AFE), Color(0xFF8E44EC), Color(0xFF0FB5C9))
    } else {
        listOf(Color(0xFF7BE3AE), Color(0xFF9DB4FF), Color(0xFFD0A8FF), Color(0xFF8FE3EE))
    }

    // Read inside the draw block only, so each frame redraws the canvas without recomposing.
    val phase: State<Float>? = if (LocalReduceMotion.current) {
        null
    } else {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 28_000, easing = LinearEasing)),
            label = "auroraPhase"
        )
    }

    Canvas(modifier = modifier) {
        drawRect(base)
        val t = (phase?.value ?: 0.2f) * 2f * PI.toFloat()
        val w = size.width
        val h = size.height
        val reach = maxOf(w, h)

        fun pool(color: Color, x: Float, y: Float, radius: Float) {
            val center = Offset(x, y)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = strength), color.copy(alpha = strength * 0.4f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // Whole-number multiples of t keep the loop seamless.
        pool(pools[0], w * (0.10f + 0.16f * sin(t)), h * (0.08f + 0.07f * cos(t)), reach * 0.55f)
        pool(pools[1], w * (0.92f + 0.12f * cos(t)), h * (0.30f + 0.10f * sin(2f * t)), reach * 0.50f)
        pool(pools[2], w * (0.20f + 0.14f * cos(2f * t)), h * (0.78f + 0.08f * sin(t)), reach * 0.52f)
        pool(pools[3], w * (0.85f + 0.10f * sin(t)), h * (0.95f + 0.06f * cos(2f * t)), reach * 0.45f)
    }
}
