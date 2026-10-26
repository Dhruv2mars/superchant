package com.superchant.bridge

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.superchant.data.ChantState
import com.superchant.engine.R
import com.superchant.service.ChantingService
import com.superchant.ui.CounterDisplay
import com.superchant.ui.SuperChantColors
import com.superchant.ui.TapToCountPad
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ComposableScope
import expo.modules.kotlin.views.ComposeProps
import expo.modules.kotlin.views.ExpoComposeView

data class CounterProps(
    /** Dark colors (the app's theme setting, resolved in JS). */
    val dark: MutableState<Boolean> = mutableStateOf(false),
    /** Volume buttons can count: the accessibility service is on and its disclosure was agreed to. */
    val volumeReady: MutableState<Boolean> = mutableStateOf(false)
) : ComposeProps

/**
 * The mala ring and tap-to-count pad from the Kotlin app's HomeScreen (CounterArea), as a native view. It
 * collects ChantingService's state itself, so a count (volume key or tap) redraws only this view and never
 * runs JavaScript.
 */
class CounterView(context: Context, appContext: AppContext) :
    ExpoComposeView<CounterProps>(context, appContext, withHostingView = true) {
    override val props = CounterProps()

    @Composable
    override fun ComposableScope.Content() {
        SuperChantColors(dark = props.dark.value) {
            CounterArea(isDarkTheme = props.dark.value, volumeReady = props.volumeReady.value)
        }
    }
}

@Composable
private fun CounterArea(isDarkTheme: Boolean, volumeReady: Boolean) {
    val holder = ChantingService.chantStateFlow.collectAsState()
    val isActive by remember { derivedStateOf { holder.value.isActive } }
    if (!isActive) {
        CounterDisplay(state = ChantState(), isDarkTheme = isDarkTheme, modifier = Modifier.fillMaxSize())
        return
    }
    val isPaused by remember { derivedStateOf { holder.value.isPaused } }
    val isGoalComplete by remember { derivedStateOf { holder.value.isGoalComplete } }
    val touchCounting by remember { derivedStateOf { holder.value.touchCounting } }
    val rounds by remember { derivedStateOf { holder.value.rounds } }
    val statusText = stringResource(
        when {
            isGoalComplete -> R.string.status_goal_complete
            isPaused -> R.string.status_paused_hint
            touchCounting && volumeReady -> R.string.status_both
            touchCounting -> R.string.status_tap
            volumeReady -> R.string.status_volume
            else -> R.string.status_touch_off
        }
    )
    val roundsText = if (rounds > 0) pluralStringResource(R.plurals.rounds_count, rounds, rounds) else null
    val state = holder.value
    TapToCountPad(
        enabled = touchCounting && !isPaused && !isGoalComplete,
        countDescription = stringResource(
            R.string.count_description,
            state.currentRoundCount,
            ChantState.ROUND_SIZE,
            pluralStringResource(R.plurals.rounds_count, state.rounds, state.rounds)
        ),
        onCount = ChantingService::countFromScreen,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CounterDisplay(
                state = state,
                isDarkTheme = isDarkTheme,
                footer = roundsText,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }
    }
}
