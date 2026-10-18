package com.superchant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.superchant.PerfTrace
import com.superchant.data.ChantState
import com.superchant.ui.theme.CounterGold
import com.superchant.ui.theme.CounterGoldDark
import com.superchant.ui.theme.EaseOutStrong
import com.superchant.ui.theme.ProgressTrackDark
import com.superchant.ui.theme.ProgressTrackLight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Progress ring with the count of the current round, drawn like a mala: beads around the ring (one per
 * count when they fit, otherwise one per group of counts) and a larger meru bead at the top where each
 * round starts and ends. Everything scales with the space given. Motion is deliberately calm: no bounce,
 * a barely-there pulse on each count (it happens 108 times a round) and a slow glow when a round completes.
 * [footer] is an optional line under "/ 108".
 */
@Composable
fun CounterDisplay(
    state: ChantState,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    footer: String? = null
) {
    val currentCount = state.currentRoundCount
    val progress = state.progress

    val animatedProgress = remember { Animatable(progress) }
    LaunchedEffect(state.count) {
        if (state.count > 0 && currentCount == 0 && animatedProgress.value > 0.5f) {
            // A round just completed: close the circle, then start again from the meru bead.
            animatedProgress.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
            animatedProgress.snapTo(0f)
        } else {
            animatedProgress.animateTo(
                targetValue = progress,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    // A slow glow when a round completes (not when the screen first shows a session)
    val glow = remember { Animatable(0f) }
    val roundsSeen = remember { mutableIntStateOf(state.rounds) }
    LaunchedEffect(state.rounds) {
        val completed = state.rounds > roundsSeen.intValue
        roundsSeen.intValue = state.rounds
        if (completed) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(1600, easing = FastOutSlowInEasing))
        } else {
            glow.snapTo(0f)
        }
    }

    // A near-imperceptible pulse on each count: the core action, so it stays well under 150 ms
    val scale = remember { Animatable(1f) }
    LaunchedEffect(state.count) {
        if (state.count > 0) {
            scale.snapTo(1.03f)
            scale.animateTo(1f, tween(140, easing = EaseOutStrong))
        }
    }

    val progressColor = if (isDarkTheme) CounterGoldDark else CounterGold
    val trackColor = if (isDarkTheme) ProgressTrackDark else ProgressTrackLight

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        val availableSize = min(maxWidth, maxHeight)
        // Scale the count with the ring so it fits on small screens and fills large ones
        val countSize = with(LocalDensity.current) { (availableSize * 0.22f).coerceIn(32.dp, 96.dp).toSp() }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(availableSize)
                .aspectRatio(1f)
        ) {
            val drawnCount = state.count
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                PerfTrace.countDrawn(drawnCount)
                PerfTrace.section("SC.ringDraw") {
                    val outer = size.minDimension / 2
                    val geometry = MalaGeometry.forRing(outerRadius = outer, density = density)
                    val center = Offset(size.width / 2, size.height / 2)
                    val ringRadius = geometry.ringRadius

                    drawCircle(
                        color = trackColor,
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = geometry.strokeWidth)
                    )

                    if (glow.value > 0f) {
                        drawCircle(
                            color = progressColor.copy(alpha = 0.35f * glow.value),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = geometry.strokeWidth * (1f + 2.5f * glow.value))
                        )
                    }

                    val sweepAngle = animatedProgress.value * 360f
                    if (sweepAngle > 0) {
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                            size = Size(ringRadius * 2, ringRadius * 2),
                            style = Stroke(width = geometry.strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Beads sit between the meru gap at the top; bead i lights once its share of the round is done.
                    val beadCount = geometry.beadCount
                    for (i in 0 until beadCount) {
                        val angle = MalaGeometry.beadAngle(i, beadCount)
                        val isLit = animatedProgress.value >= (i + 1f) / beadCount - 0.0001f
                        drawCircle(
                            color = if (isLit) progressColor else trackColor,
                            radius = if (isLit) geometry.beadRadius else geometry.beadRadius * 0.62f,
                            center = Offset(
                                center.x + geometry.beadOrbit * cos(angle).toFloat(),
                                center.y + geometry.beadOrbit * sin(angle).toFloat()
                            )
                        )
                    }

                    // Meru bead: start and end of every round
                    val meruCenter = Offset(center.x, center.y - geometry.beadOrbit)
                    drawCircle(color = progressColor, radius = geometry.meruRadius, center = meruCenter)
                    drawCircle(
                        color = trackColor,
                        radius = geometry.meruRadius * 0.42f,
                        center = meruCenter
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentCount.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = countSize,
                        lineHeight = countSize
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    // Read in the layer block so the pop only redraws, without recomposing each frame
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                )
                Text(
                    text = "/ ${ChantState.ROUND_SIZE}",
                    style = if (availableSize < 220.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (footer != null) {
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/** Sizes for the mala ring, derived from the space available so beads never crowd or vanish. */
internal data class MalaGeometry(
    val ringRadius: Float,
    val strokeWidth: Float,
    val beadRadius: Float,
    val beadOrbit: Float,
    val meruRadius: Float,
    val beadCount: Int
) {
    companion object {
        /** Beads need at least this much arc per bead, in bead radii, to read as separate beads. */
        private const val MIN_SPACING_RADII = 3.2f

        fun forRing(outerRadius: Float, density: Float): MalaGeometry {
            val beadRadius = (outerRadius * 0.028f).coerceIn(2.5f * density, 6f * density)
            val meruRadius = beadRadius * 1.7f
            val beadOrbit = outerRadius - meruRadius
            val strokeWidth = (outerRadius * 0.05f).coerceIn(5f * density, 12f * density)
            val ringRadius = beadOrbit - meruRadius - strokeWidth / 2 - 4f * density
            val maxBeads = ((2 * PI * beadOrbit) / (beadRadius * MIN_SPACING_RADII)).toInt() - 2
            return MalaGeometry(
                ringRadius = ringRadius,
                strokeWidth = strokeWidth,
                beadRadius = beadRadius,
                beadOrbit = beadOrbit,
                meruRadius = meruRadius,
                beadCount = beadsToShow(maxBeads)
            )
        }

        /** One bead per count when all 108 fit; otherwise the most beads that split the round evenly (54 of 2, 36 of 3, ...). */
        fun beadsToShow(maxBeads: Int): Int =
            (minOf(maxBeads, ChantState.ROUND_SIZE) downTo 1).firstOrNull { ChantState.ROUND_SIZE % it == 0 } ?: 0

        /** Angle of bead [index], leaving the top position free for the meru bead. */
        fun beadAngle(index: Int, count: Int): Double = (index + 0.5) / count * 2 * PI - PI / 2
    }
}
