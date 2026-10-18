package com.superchant.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Motion tokens. UI motion stays under 300 ms and starts fast: things that appear or answer a touch
 * use [EaseOutStrong]; nothing uses ease-in. The system "Remove animations" setting scales all of it.
 */

/** Strong ease-out for entering, revealing and responding to a press. */
val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Press feedback for a pressable: shrinks slightly the instant a finger lands (100 ms) and settles back
 * on release (160 ms), so the control feels like it heard the touch. Read in the layer, so it only redraws.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(if (pressed) 100 else 160, easing = EaseOutStrong),
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
