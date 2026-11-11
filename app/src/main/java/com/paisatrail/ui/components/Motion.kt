package com.paisatrail.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.paisatrail.domain.util.Money
import kotlinx.coroutines.delay

/**
 * True when the person has switched on "Reduce motion" in Settings. Every decorative animation
 * in the app checks this and falls back to its final, still state.
 */
val LocalReduceMotion = compositionLocalOf { false }

/** Items after this position rise in without a stagger delay, so scrolling never feels held back. */
private const val MAX_STAGGERED_ITEMS = 8
private const val STAGGER_STEP_MS = 45L

/**
 * A click target that squashes slightly while pressed and springs back on release,
 * the way buttons do on iOS.
 */
@Composable
fun Modifier.bouncyClickable(
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        // Clip before the click target so the touch ripple follows the shape.
        .clip(shape)
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Entrance: the element fades in while rising and growing a little into place.
 * Give siblings increasing [index] values to get a cascade.
 */
@Composable
fun Modifier.riseIn(index: Int = 0): Modifier {
    if (LocalReduceMotion.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (index in 1 until MAX_STAGGERED_ITEMS) delay(index * STAGGER_STEP_MS)
        progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 28.dp.toPx()
        val grow = 0.96f + 0.04f * p
        scaleX = grow
        scaleY = grow
    }
}

/** Pops in from nothing with an overshoot. Used for success badges. */
@Composable
fun Modifier.popIn(delayMillis: Long = 0L): Modifier {
    if (LocalReduceMotion.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis)
        progress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        scaleX = p
        scaleY = p
    }
}

/** A slow, endless breathing pulse for small accent icons. */
@Composable
fun Modifier.breathe(): Modifier {
    if (LocalReduceMotion.current) return this
    val transition = rememberInfiniteTransition(label = "breathe")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathePhase"
    )
    return this.graphicsLayer {
        val p = phase.value
        scaleX = 0.9f + 0.2f * p
        scaleY = 0.9f + 0.2f * p
        alpha = 0.7f + 0.3f * p
    }
}

/** A gentle, endless up-and-down float for empty-state artwork. */
@Composable
fun Modifier.floatSlowly(): Modifier {
    if (LocalReduceMotion.current) return this
    val transition = rememberInfiniteTransition(label = "float")
    val phase = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatPhase"
    )
    return this.graphicsLayer { translationY = phase.value * 5.dp.toPx() }
}

/** A band of light that sweeps across the element, for anything that is still loading. */
@Composable
fun Modifier.shimmer(highlight: Color = Color.White.copy(alpha = 0.22f)): Modifier {
    if (LocalReduceMotion.current) return this
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "shimmerPhase"
    )
    return this.drawWithContent {
        drawContent()
        val band = size.width * 0.6f
        val startX = phase.value * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(startX, 0f),
                end = Offset(startX + band, size.height)
            )
        )
    }
}

/**
 * Counts from the previously shown amount to [targetPaise]. The interpolation happens in whole
 * paise, and the final frame is always the exact target, so no rounding error is ever displayed.
 */
@Composable
fun animatedPaise(targetPaise: Long): Long {
    if (LocalReduceMotion.current) return targetPaise
    var shown by remember { mutableStateOf(0L) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(targetPaise) {
        val start = shown
        if (start == targetPaise) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 850, easing = FastOutSlowInEasing)) {
            shown = start + ((targetPaise - start) * value.toDouble()).toLong()
        }
        shown = targetPaise
    }
    return shown
}

/** A rupee amount that rolls to its value whenever it changes. */
@Composable
fun AnimatedAmount(
    paise: Long,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = FontWeight.Bold,
    color: Color = Color.Unspecified,
    showPaise: Boolean? = null
) {
    val shown = animatedPaise(paise)
    // While the number is rolling, hide the paise so the text width doesn't flicker.
    val text = when {
        shown != paise -> Money.format(shown, false)
        showPaise == null -> Money.format(shown)
        else -> Money.format(shown, showPaise)
    }
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
