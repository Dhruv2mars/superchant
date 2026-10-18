package com.superchant.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.superchant.R
import com.superchant.data.CountDebouncer
import com.superchant.data.InputMode
import com.superchant.ui.theme.FocusText
import kotlinx.coroutines.launch

/**
 * Large area that counts one bead per touch. Counting happens on finger-down for an immediate,
 * bead-like response; extra fingers are ignored until every finger lifts, and repeats faster than
 * [CountDebouncer.DEFAULT_MIN_INTERVAL_MS] are dropped. For TalkBack the whole area is one node that
 * reads [countDescription], announces changes politely, and counts on double-tap.
 */
@Composable
fun TapToCountPad(
    enabled: Boolean,
    countDescription: String,
    onCount: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    pressedColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    val currentOnCount by rememberUpdatedState(onCount)
    val debouncer = remember { CountDebouncer() }
    var pressed by remember { mutableStateOf(false) }
    val pressAlpha by animateFloatAsState(if (pressed) 0.10f else 0f, tween(if (pressed) 40 else 220), label = "press")
    val tapActionLabel = stringResource(R.string.tap_to_count_action)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(if (enabled) containerColor else Color.Transparent)
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
                if (enabled) {
                    onClick(label = tapActionLabel) { currentOnCount(); true }
                } else {
                    disabled()
                }
            },
        content = content
    )
}

/**
 * Undo that must be pressed and held, so a stray touch can't remove counts. TalkBack users get a normal
 * double-tap action since their activation is already deliberate.
 */
@Composable
fun HoldToUndoButton(
    enabled: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    HoldButton(
        enabled = enabled,
        onConfirm = onUndo,
        hint = stringResource(R.string.undo_hold_hint),
        description = stringResource(R.string.undo_description),
        actionLabel = stringResource(R.string.undo_action),
        containerColor = if (dimmed) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (dimmed) FocusText else MaterialTheme.colorScheme.onSecondaryContainer,
        fillColor = if (dimmed) FocusText.copy(alpha = 0.25f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
        modifier = modifier.widthIn(min = 96.dp)
    ) { color ->
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Undo,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(R.string.undo_label),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Stop for a running session. Once anything has been counted it must be held, so a brush of the thumb
 * can't end a long session; with nothing counted a tap stops at once.
 */
@Composable
fun HoldToStopButton(
    holdRequired: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    HoldButton(
        enabled = true,
        holdRequired = holdRequired,
        holdMillis = 700,
        onConfirm = onStop,
        hint = stringResource(R.string.stop_hold_hint),
        description = stringResource(R.string.action_stop),
        actionLabel = stringResource(R.string.action_stop),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        fillColor = MaterialTheme.colorScheme.error.copy(alpha = 0.28f),
        modifier = modifier.widthIn(min = 88.dp)
    ) { color ->
        Text(
            text = stringResource(R.string.action_stop),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

/**
 * Pill button that fires only after being pressed for [holdMillis] (unless [holdRequired] is false).
 * A fill sweeps across while holding and a haptic tick confirms. A short tap shows [hint] in a tooltip
 * instead of changing the label, so the button never changes size. TalkBack activates it directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldButton(
    enabled: Boolean,
    onConfirm: () -> Unit,
    hint: String,
    description: String,
    actionLabel: String,
    containerColor: Color,
    contentColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
    holdRequired: Boolean = true,
    holdMillis: Int = 450,
    content: @Composable RowScope.(contentColor: Color) -> Unit
) {
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val progress = remember { Animatable(0f) }
    val tooltipState = rememberTooltipState()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(hint) } },
        state = tooltipState,
        enableUserInput = false
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = modifier
                .heightIn(min = 48.dp)
                .alpha(if (enabled) 1f else 0.38f)
                .clip(RoundedCornerShape(24.dp))
                .background(containerColor)
                .drawBehind {
                    if (progress.value > 0f) drawRect(fillColor, size = Size(size.width * progress.value, size.height))
                }
                .pointerInput(enabled, holdRequired) {
                    if (!enabled) return@pointerInput
                    if (!holdRequired) {
                        detectTapGestures(onTap = { currentOnConfirm() })
                        return@pointerInput
                    }
                    detectTapGestures(onPress = {
                        var fired = false
                        val hold = scope.launch {
                            progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                            fired = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnConfirm()
                        }
                        try {
                            tryAwaitRelease()
                            if (!fired) scope.launch { tooltipState.show() }
                        } finally {
                            // Also when the gesture is cancelled (e.g. the button is disabled mid-press)
                            if (!fired) hold.cancel()
                            scope.launch { progress.animateTo(0f, tween(150)) }
                        }
                    })
                }
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = description
                    if (enabled) onClick(label = actionLabel) { currentOnConfirm(); true } else disabled()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            content(contentColor)
        }
    }
}

/** Segmented choice between the two ways to count, with a one-line explanation of the selected one. */
@Composable
fun InputModeSelector(
    selected: InputMode,
    onSelect: (InputMode) -> Unit,
    accessibilityEnabled: Boolean,
    onSetUpAccessibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        InputMode.ON_SCREEN to R.string.input_mode_on_screen,
        InputMode.VOLUME to R.string.input_mode_volume,
        InputMode.BOTH to R.string.input_mode_both
    )
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(stringResource(label), maxLines = 1)
                }
            }
        }
        Text(
            text = stringResource(
                when (selected) {
                    InputMode.ON_SCREEN -> R.string.input_mode_on_screen_desc
                    InputMode.VOLUME -> R.string.input_mode_volume_desc
                    InputMode.BOTH -> R.string.input_mode_both_desc
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
        )
        if (selected.usesVolumeKeys && !accessibilityEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.input_mode_needs_accessibility),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                TextButton(onClick = onSetUpAccessibility) {
                    Text(stringResource(R.string.input_mode_set_up))
                }
            }
        }
    }
}

/**
 * True while the proximity sensor reports something close, e.g. the phone is in a pocket or face down
 * on a cushion. Listens only while [enabled] and the screen is resumed; false without a sensor.
 */
@Composable
fun rememberProximityCovered(enabled: Boolean): Boolean {
    val context = LocalContext.current
    var covered by remember { mutableStateOf(false) }
    LifecycleResumeEffect(enabled) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (!enabled || sensor == null) {
            covered = false
            return@LifecycleResumeEffect onPauseOrDispose { }
        }
        // Most sensors are binary (0 or max range); treat anything nearer than 5 cm as covered.
        val near = minOf(sensor.maximumRange, 5f)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                covered = event.values[0] < near
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onPauseOrDispose {
            sensorManager.unregisterListener(listener)
            covered = false
        }
    }
    return covered
}

/** Keeps the screen on while [enabled] and this UI is visible; the system drops it when the app leaves the foreground. */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** While [enabled], dims this window and hides the system bars for the focus layout. */
@Composable
fun FocusWindowEffect(enabled: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        window.attributes = window.attributes.apply { screenBrightness = FOCUS_BRIGHTNESS }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private const val FOCUS_BRIGHTNESS = 0.1f
