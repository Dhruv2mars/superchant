package com.superchant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.superchant.R
import com.superchant.data.ChantState
import com.superchant.data.Goal
import com.superchant.data.PreferencesRepository
import com.superchant.service.ChantingService
import com.superchant.service.VolumeKeyAccessibilityService
import com.superchant.ui.components.AccessibilitySetupDialog
import com.superchant.ui.components.AppTopBar
import com.superchant.ui.components.CounterDisplay
import com.superchant.ui.components.KeepScreenOn
import com.superchant.ui.components.StartButton
import com.superchant.ui.components.StopButton
import com.superchant.ui.components.TapToCountPad
import com.superchant.ui.components.TodayLine
import com.superchant.ui.components.TouchCountingToggle
import com.superchant.ui.theme.ThemeMode
import com.superchant.ui.theme.pressScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home: the counter. Idle, it shows the empty ring and Start. During a session it shows the controls
 * (touch counting, pause, Stop) above the goal strip and the counter, which takes all remaining space
 * so the controls and goal never get pushed off-screen.
 *
 * Volume buttons always count once the accessibility service is enabled and agreed to. Touch counting
 * is a per-session switch: on, the counter becomes the tap target and the screen stays awake. It starts
 * on when volume buttons can't count yet, so Start never waits on a permission.
 *
 * [pendingStart] is a start requested elsewhere (a goal on the Goals screen, a launcher shortcut or the
 * Quick Settings tile); it goes through the same notification permission check as Start.
 */
@Composable
fun HomeScreen(
    themeMode: ThemeMode,
    preferencesRepository: PreferencesRepository,
    pendingStart: StartRequest?,
    onPendingStartHandled: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onGoalsClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    // The count changes on every press, so it is only read where it's shown (CounterArea);
    // this screen reads coarse derived values and recomposes only when those change.
    val chantStateHolder = ChantingService.chantStateFlow.collectAsStateWithLifecycle()
    val chantState = remember(chantStateHolder) { { chantStateHolder.value } }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Derived from the service's state rather than set optimistically on tap, so it can't stick
    val isServiceRunning by remember { derivedStateOf { chantStateHolder.value.isActive } }
    val isPaused by remember { derivedStateOf { chantStateHolder.value.isPaused } }
    val isGoalComplete by remember { derivedStateOf { chantStateHolder.value.isGoalComplete } }
    val touchCounting by remember { derivedStateOf { chantStateHolder.value.touchCounting } }
    val rounds by remember { derivedStateOf { chantStateHolder.value.rounds } }
    // Goal progress without the per-press fields, so the goal strip only changes at round boundaries
    val goalProgress by remember {
        derivedStateOf { chantStateHolder.value.copy(count = 0, rounds = 0, lastUpdatedAt = 0L) }
    }

    // Track accessibility service enabled state
    var isAccessibilityEnabled by remember {
        mutableStateOf(VolumeKeyAccessibilityService.isAccessibilityServiceEnabled(context))
    }
    // null until loaded; Start waits for it so a session never starts on a guess
    val savedConsent by preferencesRepository.accessibilityConsent.collectAsStateWithLifecycle()
    val accessibilityConsent = savedConsent == true
    val volumeReady = isAccessibilityEnabled && accessibilityConsent
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    // Permission handling for notifications (Android 13+)
    fun notificationsGranted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var hasNotificationPermission by remember { mutableStateOf(notificationsGranted()) }

    // Re-check accessibility when returning to app (user might have enabled it in settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = VolumeKeyAccessibilityService.isAccessibilityServiceEnabled(context)
                hasNotificationPermission = notificationsGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    KeepScreenOn(enabled = isServiceRunning && touchCounting)

    // A start waiting on the permission prompt
    var goalToStart by remember { mutableStateOf<Goal?>(null) }
    var startPending by remember { mutableStateOf(false) }

    fun beginSession() {
        val goal = goalToStart
        goalToStart = null
        startPending = false
        ChantingService.start(context, goal, touchCounting = !volumeReady)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!startPending) return@rememberLauncherForActivityResult
        // Counting doesn't need the notification (the service runs either way), so start and say why it's missing
        if (!isGranted) Toast.makeText(context, R.string.notifications_off_hint, Toast.LENGTH_LONG).show()
        beginSession()
    }

    fun requestStart(goal: Goal?) {
        goalToStart = goal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            startPending = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            beginSession()
        }
    }

    // A brief, quiet acknowledgement after Stop; read from the state only at that moment
    var lastSummary by remember { mutableStateOf<SessionSummary?>(null) }
    LaunchedEffect(lastSummary) {
        if (lastSummary != null) {
            delay(SUMMARY_VISIBLE_MS)
            lastSummary = null
        }
    }

    val onStop: () -> Unit = {
        lastSummary = SessionSummary.of(chantStateHolder.value, System.currentTimeMillis())
        ChantingService.stop(context)
    }
    val onStart: () -> Unit = { if (savedConsent != null) requestStart(goal = null) }

    LaunchedEffect(pendingStart, savedConsent) {
        val request = pendingStart ?: return@LaunchedEffect
        if (savedConsent == null) return@LaunchedEffect
        onPendingStartHandled()
        // A plain "start counting" while a session runs just shows it; a goal starts over (saving the current one)
        if (request.goal != null || !isServiceRunning) requestStart(request.goal)
    }

    if (showAccessibilityDialog) {
        AccessibilitySetupDialog(
            consented = accessibilityConsent,
            onConsent = {
                preferencesRepository.write { setAccessibilityConsent(true) }
                // Already enabled (e.g. from an older version): nothing left to set up
                if (isAccessibilityEnabled) showAccessibilityDialog = false
            },
            onDismiss = { showAccessibilityDialog = false },
            onOpenSettings = {
                showAccessibilityDialog = false
                VolumeKeyAccessibilityService.openAccessibilitySettings(context)
            }
        )
    }

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val touchHintSeen = preferencesRepository.touchHintSeen.collectAsStateWithLifecycle().value
    val session = SessionUi(
        state = chantState,
        tapEnabled = touchCounting && !isPaused && !isGoalComplete,
        statusText = stringResource(
            when {
                isGoalComplete -> R.string.status_goal_complete
                isPaused -> R.string.status_paused_hint
                touchCounting && volumeReady -> R.string.status_both
                touchCounting -> R.string.status_tap
                volumeReady -> R.string.status_volume
                else -> R.string.status_touch_off
            }
        ),
        roundsText = if (rounds > 0) pluralStringResource(R.plurals.rounds_count, rounds, rounds) else null,
        touchCounting = touchCounting,
        showTouchHint = touchHintSeen == false,
        onTouchHintShown = { preferencesRepository.write { setTouchHintSeen() } },
        onPauseToggle = { ChantingService.setPaused(!chantStateHolder.value.isPaused) },
        isPaused = isPaused,
        isGoalComplete = isGoalComplete
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = onGoalsClick) {
                        Icon(imageVector = Icons.Outlined.Flag, contentDescription = stringResource(R.string.goals))
                    }
                    IconButton(onClick = onHistoryClick) {
                        Icon(imageVector = Icons.Outlined.History, contentDescription = stringResource(R.string.history))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(imageVector = Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        val sessionControls: @Composable (Modifier) -> Unit = { modifier ->
            SessionControls(session = session, onStop = onStop, modifier = modifier)
        }
        val goalStrip: @Composable () -> Unit = {
            goalProgress.activeGoal?.let { goal -> GoalStrip(state = goalProgress, goal = goal) }
        }
        // Offered whenever volume buttons can't count yet; goes through the disclosure first
        val volumeOffer: @Composable (Modifier) -> Unit = { modifier ->
            if (!volumeReady) VolumeButtonsOffer(onClick = { showAccessibilityDialog = true }, modifier = modifier)
        }
        val idleControls: @Composable (Modifier) -> Unit = { modifier ->
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = lastSummary != null, enter = fadeIn(), exit = fadeOut()) {
                    lastSummary?.let { SessionSavedLine(it) }
                }
                val sessions = preferencesRepository.sessionHistory.collectAsStateWithLifecycle().value
                val target = preferencesRepository.dailyTarget.collectAsStateWithLifecycle().value
                if (sessions != null && target != null) TodayLine(sessions = sessions, dailyTarget = target)
                StartButton(onClick = onStart, modifier = Modifier.fillMaxWidth(0.7f))
            }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CounterArea(
                    session = session,
                    isActive = isServiceRunning,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isServiceRunning) {
                        sessionControls(Modifier.fillMaxWidth())
                        goalStrip()
                        volumeOffer(Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        idleControls(Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isServiceRunning) {
                    sessionControls(Modifier.fillMaxWidth().padding(top = 4.dp))
                    goalStrip()
                }
                // The counter takes whatever is left, so everything else always fits
                CounterArea(
                    session = session,
                    isActive = isServiceRunning,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                if (isServiceRunning) {
                    volumeOffer(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    idleControls(Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/** What a just-stopped session added, shown briefly on Home. */
private data class SessionSummary(val counts: Int, val rounds: Int, val minutes: Long) {
    companion object {
        fun of(state: ChantState, now: Long): SessionSummary? =
            if (state.count > 0) SessionSummary(state.count, state.rounds, (now - state.sessionStartTime).coerceAtLeast(0) / 60_000) else null
    }
}

private const val SUMMARY_VISIBLE_MS = 6_000L

/** How long the one-time touch-counting hint stays up, after a short beat for the screen to settle. */
private const val TOUCH_HINT_DELAY_MS = 600L
private const val TOUCH_HINT_VISIBLE_MS = 5_000L

@Composable
private fun SessionSavedLine(summary: SessionSummary) {
    val parts = listOfNotNull(
        stringResource(R.string.session_saved),
        if (summary.rounds > 0) pluralStringResource(R.plurals.rounds_count, summary.rounds, summary.rounds)
        else pluralStringResource(R.plurals.insights_counts, summary.counts, summary.counts),
        if (summary.minutes > 0) pluralStringResource(R.plurals.minutes_short, summary.minutes.toInt(), summary.minutes.toInt()) else null
    )
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** A session start asked for from outside Home; [goal] null is a free session. */
data class StartRequest(val goal: Goal?)

/**
 * Everything the session layouts need, resolved once in [HomeScreen]. [state] is read lazily by the
 * composables that show the count, so a press doesn't recompose the rest of the screen.
 */
private data class SessionUi(
    val state: () -> ChantState,
    val tapEnabled: Boolean,
    val statusText: String,
    val roundsText: String?,
    val touchCounting: Boolean,
    val showTouchHint: Boolean,
    val onTouchHintShown: () -> Unit,
    val onPauseToggle: () -> Unit,
    val isPaused: Boolean,
    val isGoalComplete: Boolean
)

/** TalkBack description of the count, e.g. "Count 42 of 108. 3 rounds". */
@Composable
private fun countDescription(state: ChantState): String = stringResource(
    R.string.count_description,
    state.currentRoundCount,
    ChantState.ROUND_SIZE,
    pluralStringResource(R.plurals.rounds_count, state.rounds, state.rounds)
)

/** The ring; during a session it sits in the tap-to-count pad with the status line underneath. */
@Composable
private fun CounterArea(
    session: SessionUi,
    isActive: Boolean,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) {
        CounterDisplay(state = ChantState(), isDarkTheme = isDarkTheme, modifier = modifier)
        return
    }
    val state = session.state()
    TapToCountPad(
        enabled = session.tapEnabled,
        countDescription = countDescription(state),
        onCount = ChantingService::countFromScreen,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CounterDisplay(
                state = state,
                isDarkTheme = isDarkTheme,
                footer = session.roundsText,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            )
            Text(
                text = session.statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }
    }
}

/**
 * Session controls, kept at the top away from the thumb that taps the counter: the touch-counting
 * switch on the left, pause and Stop on the right. The first session ever shows one hint on the switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionControls(
    session: SessionUi,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    LaunchedEffect(session.showTouchHint) {
        if (!session.showTouchHint) return@LaunchedEffect
        delay(TOUCH_HINT_DELAY_MS)
        launch { tooltipState.show() }
        delay(TOUCH_HINT_VISIBLE_MS)
        tooltipState.dismiss()
        session.onTouchHintShown()
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = { PlainTooltip { Text(stringResource(R.string.touch_counting_hint)) } },
            state = tooltipState
        ) {
            TouchCountingToggle(checked = session.touchCounting, onCheckedChange = ChantingService::setTouchCounting)
        }
        Spacer(modifier = Modifier.weight(1f))
        val paused = session.isPaused
        val pauseInteraction = remember { MutableInteractionSource() }
        FilledTonalIconButton(
            onClick = session.onPauseToggle,
            enabled = !session.isGoalComplete,
            interactionSource = pauseInteraction,
            modifier = Modifier
                .size(48.dp)
                .pressScale(pauseInteraction)
        ) {
            Icon(
                imageVector = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription = stringResource(if (paused) R.string.action_resume else R.string.action_pause)
            )
        }
        StopButton(onClick = onStop)
    }
}

/** Quiet offer to count with the volume buttons too, shown while they can't. */
@Composable
private fun VolumeButtonsOffer(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.volume_buttons_offer))
    }
}

/**
 * Compact goal progress: goal name, current mantra and overall progress in a fixed-height card.
 * Tap to expand the full mantra list (height-capped and scrollable).
 */
@Composable
private fun GoalStrip(state: ChantState, goal: Goal) {
    var expanded by rememberSaveable(goal.id) { mutableStateOf(false) }
    val isGoalComplete = state.isGoalComplete
    val mantra = state.currentMantra
    val roundsDone = goal.mantras.take(state.currentMantraIndex).sumOf { it.targetRounds } + state.mantraRoundsCompleted

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGoalComplete) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isGoalComplete) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(R.string.goal_complete),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.goal_mantra_position, state.currentMantraIndex + 1, goal.mantras.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.goal_hide_mantras else R.string.goal_show_mantras),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (isGoalComplete || mantra == null) {
                    Text(
                        text = pluralStringResource(R.plurals.goal_all_rounds_done, goal.totalRounds, goal.totalRounds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = mantra.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.goal_mantra_progress,
                            mantra.targetRounds,
                            state.mantraRoundsCompleted,
                            mantra.targetRounds
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (goal.totalRounds > 0) roundsDone.toFloat() / goal.totalRounds else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            AnimatedVisibility(visible = expanded) {
                MantraList(
                    goal = goal,
                    currentMantraIndex = state.currentMantraIndex,
                    mantraRoundsCompleted = state.mantraRoundsCompleted,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun MantraList(
    goal: Goal,
    currentMantraIndex: Int,
    mantraRoundsCompleted: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        goal.mantras.forEachIndexed { index, mantra ->
            val isCurrentMantra = index == currentMantraIndex
            val isCompleted = index < currentMantraIndex ||
                (index == currentMantraIndex && mantraRoundsCompleted >= mantra.targetRounds)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> MaterialTheme.colorScheme.primary
                                isCurrentMantra -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = stringResource(R.string.goal_mantra_completed),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrentMantra) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = mantra.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrentMantra) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isCurrentMantra) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isCurrentMantra && !isCompleted) {
                        pluralStringResource(R.plurals.goal_mantra_progress, mantra.targetRounds, mantraRoundsCompleted, mantra.targetRounds)
                    } else {
                        pluralStringResource(R.plurals.rounds_count, mantra.targetRounds, mantra.targetRounds)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentMantra && !isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
