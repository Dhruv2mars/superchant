package com.superchant.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superchant.engine.R
import com.superchant.data.CountDebouncer
import com.superchant.ui.EaseOutStrong

/**
 * The counter area. While [enabled] (touch counting on, not paused) a soft tinted surface grows in under
 * the ring and every touch counts one bead: on finger-down for an immediate, bead-like response; extra
 * fingers are ignored until every finger lifts, and repeats faster than
 * [CountDebouncer.DEFAULT_MIN_INTERVAL_MS] are dropped. Off, the surface recedes and touches do nothing.
 * For TalkBack the whole area is one node that reads [countDescription], announces changes politely,
 * and counts on double-tap while enabled.
 */
@Composable
fun TapToCountPad(
    enabled: Boolean,
    countDescription: String,
    onCount: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val currentOnCount by rememberUpdatedState(onCount)
    val debouncer = remember { CountDebouncer() }
    var pressed by remember { mutableStateOf(false) }
    val pressAlpha by animateFloatAsState(if (pressed) 0.10f else 0f, tween(if (pressed) 40 else 220), label = "press")
    // Reveal grows in a little slower than it recedes: turning taps on is the moment worth noticing
    val reveal by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(if (enabled) 240 else 160, easing = EaseOutStrong),
        label = "reveal"
    )
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val edgeColor = MaterialTheme.colorScheme.primary
    val pressedColor = MaterialTheme.colorScheme.primary
    val tapActionLabel = stringResource(R.string.tap_to_count_action)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(PadShape)
            // Animated values are read only while drawing, so the reveal and press never recompose
            .drawBehind {
                if (reveal <= 0f) return@drawBehind
                val inset = (1f - reveal) * 12.dp.toPx()
                val radius = CornerRadius(28.dp.toPx())
                val topLeft = Offset(inset, inset)
                val rectSize = Size(size.width - 2 * inset, size.height - 2 * inset)
                drawRoundRect(surfaceColor.copy(alpha = 0.45f * reveal), topLeft, rectSize, radius)
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    edgeColor.copy(alpha = 0.22f * reveal),
                    topLeft + Offset(stroke / 2, stroke / 2),
                    Size(rectSize.width - stroke, rectSize.height - stroke),
                    radius,
                    style = Stroke(stroke)
                )
            }
            .drawWithContent {
                drawContent()
                if (pressAlpha > 0f) drawRect(pressedColor.copy(alpha = pressAlpha))
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        if (debouncer.tryAccept(down.uptimeMillis)) currentOnCount()
                        // Swallow the rest of this gesture, including any extra fingers, until all lift
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        pressed = false
                    }
                } finally {
                    // Also reached when the tap that completes a goal disables the pad mid-press
                    pressed = false
                }
            }
            .clearAndSetSemantics {
                contentDescription = countDescription
                liveRegion = LiveRegionMode.Polite
                // Off, the node still reads the count (volume buttons may be counting) but offers no action
                if (enabled) onClick(label = tapActionLabel) { currentOnCount(); true }
            },
        content = content
    )
}

private val PadShape = RoundedCornerShape(28.dp)
