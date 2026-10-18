package com.superchant.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.superchant.data.ChantState
import com.superchant.ui.theme.CounterGold
import com.superchant.ui.theme.CounterGoldDark
import com.superchant.ui.theme.ProgressTrackDark
import com.superchant.ui.theme.ProgressTrackLight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CounterDisplay(
    state: ChantState,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val currentCount = state.currentRoundCount
    val progress = state.progress
    
    // Animated progress
    val animatedProgress = remember { Animatable(0f) }
    
    LaunchedEffect(progress) {
        animatedProgress.animateTo(
            targetValue = progress,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    
    // Animated count for pop effect
    val scale = remember { Animatable(1f) }
    
    LaunchedEffect(state.count) {
        if (state.count > 0) {
            scale.animateTo(1.1f, animationSpec = tween(50))
            scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    
    val progressColor = if (isDarkTheme) CounterGoldDark else CounterGold
    val trackColor = if (isDarkTheme) ProgressTrackDark else ProgressTrackLight
    val beadColor = if (isDarkTheme) CounterGoldDark else CounterGold
    
    // Use BoxWithConstraints to adapt to available space (especially for landscape)
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Calculate the optimal size based on available space
        val availableSize = min(maxWidth, maxHeight)
        
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(availableSize)
                .aspectRatio(1f)
        ) {
            // Progress ring with beads
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val strokeWidth = 12.dp.toPx()
                val beadRadius = 6.dp.toPx()
                val ringRadius = (size.minDimension - strokeWidth) / 2 - beadRadius
                val center = Offset(size.width / 2, size.height / 2)
                
                // Track (background circle)
                drawCircle(
                    color = trackColor,
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                
                // Progress arc
                val sweepAngle = animatedProgress.value * 360f
                if (sweepAngle > 0) {
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(
                            center.x - ringRadius,
                            center.y - ringRadius
                        ),
                        size = Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                
                // Draw bead indicators (like a real mala)
                val beadCount = 27 // Traditional mala has sections
                for (i in 0 until beadCount) {
                    val angle = (i.toFloat() / beadCount) * 2 * PI - PI / 2
                    val beadX = center.x + (ringRadius + beadRadius + 8.dp.toPx()) * cos(angle).toFloat()
                    val beadY = center.y + (ringRadius + beadRadius + 8.dp.toPx()) * sin(angle).toFloat()
                    
                    val isActive = i.toFloat() / beadCount <= animatedProgress.value
                    
                    drawCircle(
                        color = if (isActive) beadColor else trackColor,
                        radius = if (isActive) beadRadius else beadRadius * 0.6f,
                        center = Offset(beadX, beadY)
                    )
                }
            }
            
            // Count display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentCount.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "/ ${ChantState.COUNTS_PER_ROUND}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RoundsDisplay(
    rounds: Int,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = rounds.toString(),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label ?: if (rounds == 1) "round" else "rounds",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
