package com.superchant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.superchant.data.InputMode
import com.superchant.data.PreferencesRepository
import com.superchant.data.RoundSize
import com.superchant.service.ChantingService
import com.superchant.service.VolumeKeyAccessibilityService
import com.superchant.ui.components.AccessibilitySetupDialog
import com.superchant.ui.components.AppTopBar
import com.superchant.ui.components.CounterDisplay
import com.superchant.ui.components.FocusWindowEffect
import com.superchant.ui.components.HoldToStopButton
import com.superchant.ui.components.HoldToUndoButton
import com.superchant.ui.components.InputModeSelector
import com.superchant.ui.components.KeepScreenOn
import com.superchant.ui.components.StartStopButton
import com.superchant.ui.components.TapToCountPad
import com.superchant.ui.components.TodayLine
import com.superchant.ui.components.rememberProximityCovered
import com.superchant.ui.theme.FocusBackground
import com.superchant.ui.theme.FocusText
import com.superchant.ui.theme.ThemeMode
import kotlinx.coroutines.flow.map

/**
 * Home: the counter. Idle, it shows the empty ring, the input-mode choice and Start. During a session
 * it shows controls (hold-to-undo, focus, Stop) above the goal strip and a large tap-to-count pad, which
 * takes all remaining space so the controls and goal never get pushed off-screen.
 *
 * [pendingGoal] is a goal started from the Goals screen; it goes through the same permission checks as Start.
 */
@Composable
fun HomeScreen(
    themeMode: ThemeMode,
    preferencesRepository: PreferencesRepository,
    pendingGoal: Goal?,
    onPendingGoalHandled: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onGoalsClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    // The count changes on every press, so it is only read where it's shown (CounterArea, focus mode);
    // this screen reads coarse derived values and recomposes only when those change.
    val chantStateHolder = ChantingService.chantStateFlow.collectAsStateWithLifecycle()
    val chantState = remember(chantStateHolder) { { chantStateHolder.value } }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Derived from the service's state rather than set optimistically on tap, so it can't stick
    val isServiceRunning by remember { derivedStateOf { chantStateHolder.value.isActive } }
    val isPaused by remember { derivedStateOf { chantStateHolder.value.isPaused } }
    val isGoalComplete by remember { derivedStateOf { chantStateHolder.value.isGoalComplete } }
    val rounds by remember { derivedStateOf { chantStateHolder.value.rounds } }
    val canUndo by remember { derivedStateOf { chantStateHolder.value.count > 0 && !chantStateHolder.value.isPaused } }
    val hasCounts by remember { derivedStateOf { chantStateHolder.value.count > 0 } }
    // Goal progress without the per-press fields, so the goal strip only changes at round boundaries
    val goalProgress by remember {
        derivedStateOf { chantStateHolder.value.copy(count = 0, rounds = 0, lastUpdatedAt = 0L) }
    }

    // Track accessibility service enabled state
    var isAccessibilityEnabled by remember {
        mutableStateOf(VolumeKeyAccessibilityService.isAccessibilityServiceEnabled(context))
    }

    // Saved input mode; `loaded` guards against starting before DataStore has been read
    val (savedInputMode, inputModeFlowLoaded) = remember(preferencesRepository) {
        preferencesRepository.inputModeFlow.map { it to true }
    }.collectAsState(initial = null to false).value
    val savedRoundSize by preferencesRepository.roundSize.collectAsStateWithLifecycle()
    val appRoundSize = savedRoundSize ?: RoundSize.DEFAULT
    val inputModeLoaded = inputModeFlowLoaded && savedRoundSize != null
    val inputMode = savedInputMode ?: InputMode.default(isAccessibilityEnabled)
    val setInputMode: (InputMode) -> Unit = { mode -> preferencesRepository.write { setInputMode(mode) } }

    val tapCounting = inputMode.usesScreen
    val volumeCounting = inputMode.usesVolumeKeys && isAccessibilityEnabled

    // Setup dialog, shown on Start (volume counting without accessibility) or from "Set up"
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    val accessibilityConsent = preferencesRepository.accessibilityConsent.collectAsStateWithLifecycle().value == true
    var accessibilityDialogStartsSession by remember { mutableStateOf(false) }
    var focusMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isServiceRunning) {
        if (!isServiceRunning) focusMode = false
    }

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

    val pocketGuardOn = preferencesRepository.countingFeel.collectAsStateWithLifecycle().value?.pocketGuard ?: true
    val pocketCovered = rememberProximityCovered(enabled = isServiceRunning && tapCounting && pocketGuardOn)
    // Covered: taps are ignored and the screen may time out as usual, instead of burning in a pocket
    KeepScreenOn(enabled = isServiceRunning && tapCounting && !pocketCovered)
    FocusWindowEffect(enabled = isServiceRunning && focusMode)
    BackHandler(enabled = isServiceRunning && focusMode) { focusMode = false }


    // A start waiting on a dialog or the permission prompt
    var goalToStart by remember { mutableStateOf<Goal?>(null) }
    var modeToStart by remember { mutableStateOf<InputMode?>(null) }

    fun beginSession(mode: InputMode) {
        val goal = goalToStart
        goalToStart = null
        modeToStart = null
        // Lock in the default on first use so it doesn't flip if accessibility is enabled later
        if (savedInputMode == null) setInputMode(mode)
        val roundSize = RoundSize.forSession(goal, appRoundSize)
        if (goal != null) {
            ChantingService.startWithGoal(context, goal, mode, roundSize)
        } else {
            ChantingService.start(context, mode, roundSize)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        val mode = modeToStart ?: return@rememberLauncherForActivityResult
        // Counting doesn't need the notification (the service runs either way), so start and say why it's missing
        if (!isGranted) Toast.makeText(context, R.string.notifications_off_hint, Toast.LENGTH_LONG).show()
        beginSession(mode)
    }

    // Accessibility is settled; ask for notifications if needed, then start
    fun continueStart(mode: InputMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            modeToStart = mode
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            beginSession(mode)
        }
    }

    fun requestStart(goal: Goal?) {
        goalToStart = goal
        if (inputMode.usesVolumeKeys && !isAccessibilityEnabled) {
            accessibilityDialogStartsSession = true
            showAccessibilityDialog = true
        } else {
            continueStart(inputMode)
        }
    }

    val onStartStopClick: () -> Unit = {
        if (isServiceRunning) {
            ChantingService.stop(context)
        } else if (inputModeLoaded) {
            requestStart(goal = null)
        }
    }

    LaunchedEffect(pendingGoal, inputModeLoaded) {
        val goal = pendingGoal ?: return@LaunchedEffect
        if (!inputModeLoaded) return@LaunchedEffect
        onPendingGoalHandled()
        requestStart(goal)
    }

    // Accessibility Setup Dialog
    if (showAccessibilityDialog) {
        AccessibilitySetupDialog(
            consented = accessibilityConsent,
            onConsent = { preferencesRepository.write { setAccessibilityConsent(true) } },
            onDismiss = {
                showAccessibilityDialog = false
                goalToStart = null
            },
            onOpenSettings = {
                showAccessibilityDialog = false
                goalToStart = null
                VolumeKeyAccessibilityService.openAccessibilitySettings(context)
            },
            onCountByTapping = {
                showAccessibilityDialog = false
                // Volume-only users switch to on-screen; "Both" keeps volume for when accessibility is enabled
                val mode = if (inputMode == InputMode.VOLUME) InputMode.ON_SCREEN else inputMode
                setInputMode(mode)
                if (accessibilityDialogStartsSession) continueStart(mode)
            }
        )
    }

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val session = SessionUi(
        state = chantState,
        tapEnabled = tapCounting && !isPaused && !isGoalComplete && !pocketCovered,
        statusText = stringResource(
            when {
                isGoalComplete -> R.string.status_goal_complete
                isPaused -> R.string.status_paused_hint
                pocketCovered -> R.string.status_pocket_guard
                tapCounting && volumeCounting -> R.string.status_both
                tapCounting -> R.string.status_tap
                else -> R.string.status_volume
            }
        ),
        roundsText = if (rounds > 0) pluralStringResource(R.plurals.rounds_count, rounds, rounds) else null,
        canUndo = canUndo,
        onCount = ChantingService::countFromScreen,
        onUndo = ChantingService::undo,
        onPauseToggle = { ChantingService.setPaused(!chantStateHolder.value.isPaused) },
        isPaused = isPaused,
        isGoalComplete = isGoalComplete,
        hasCounts = hasCounts
    )

    if (isServiceRunning && focusMode) {
        FocusContent(session = session, onExit = { focusMode = false })
        return
    }

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
        val controls: @Composable (Modifier) -> Unit = { modifier ->
            SessionControls(
                session = session,
                onFocus = { focusMode = true },
                onStop = onStartStopClick,
                modifier = modifier
            )
        }
        val idleControls: @Composable (Modifier) -> Unit = { modifier ->
            IdleControls(
                inputMode = inputMode,
                onInputModeChange = setInputMode,
                accessibilityEnabled = isAccessibilityEnabled,
                onSetUpAccessibility = {
                    accessibilityDialogStartsSession = false
                    showAccessibilityDialog = true
                },
                onStart = onStartStopClick,
                modifier = modifier,
                todayLine = {
                    val sessions = preferencesRepository.sessionHistory.collectAsStateWithLifecycle().value
                    val target = preferencesRepository.dailyTarget.collectAsStateWithLifecycle().value
                    if (sessions != null && target != null) TodayLine(sessions = sessions, dailyTarget = target)
                }
            )
        }
        val goalStrip: @Composable () -> Unit = {
            goalProgress.activeGoal?.let { goal -> GoalStrip(state = goalProgress, goal = goal) }
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
                    idleRoundSize = appRoundSize,
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
                        controls(Modifier.fillMaxWidth())
                        goalStrip()
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
                    controls(Modifier.fillMaxWidth().padding(top = 4.dp))
                    goalStrip()
                }
                // The counter takes whatever is left, so everything else always fits
                CounterArea(
                    session = session,
                    isActive = isServiceRunning,
                    idleRoundSize = appRoundSize,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                if (!isServiceRunning) {
                    idleControls(Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Everything the session layouts need, resolved once in [HomeScreen]. [state] is read lazily by the
 * composables that show the count, so a press doesn't recompose the rest of the screen.
 */
private data class SessionUi(
    val state: () -> ChantState,
    val tapEnabled: Boolean,
    val statusText: String,
    val roundsText: String?,
    val canUndo: Boolean,
    val onCount: () -> Unit,
    val onUndo: () -> Unit,
    val onPauseToggle: () -> Unit,
    val isPaused: Boolean,
    val isGoalComplete: Boolean,
    val hasCounts: Boolean
)

/** TalkBack description of the count, e.g. "Count 42 of 108. 3 rounds". */
@Composable
private fun countDescription(state: ChantState): String = stringResource(
    R.string.count_description,
    state.currentRoundCount,
    state.roundSize,
    pluralStringResource(R.plurals.rounds_count, state.rounds, state.rounds)
)

/** The ring; during a session it sits in the tap-to-count pad with the status line underneath. */
@Composable
private fun CounterArea(
    session: SessionUi,
    isActive: Boolean,
    idleRoundSize: Int,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) {
        CounterDisplay(state = ChantState(roundSize = idleRoundSize), isDarkTheme = isDarkTheme, modifier = modifier)
        return
    }
    val state = session.state()
    TapToCountPad(
        enabled = session.tapEnabled,
        countDescription = countDescription(state),
        onCount = session.onCount,
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

/** Session controls, kept at the top away from the thumb that taps the pad: hold-to-undo, pause, focus, hold-to-Stop. */
@Composable
private fun SessionControls(
    session: SessionUi,
    onFocus: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HoldToUndoButton(enabled = session.canUndo, onUndo = session.onUndo)
        Spacer(modifier = Modifier.weight(1f))
        val paused = session.isPaused
        FilledTonalIconButton(
            onClick = session.onPauseToggle,
            enabled = !session.isGoalComplete,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription = stringResource(if (paused) R.string.action_resume else R.string.action_pause)
            )
        }
        FilledTonalIconButton(onClick = onFocus, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = Icons.Outlined.NightsStay, contentDescription = stringResource(R.string.focus_enter))
        }
        HoldToStopButton(holdRequired = session.hasCounts, onStop = onStop)
    }
}

/** Idle: the two ways to count, then Start. */
@Composable
private fun IdleControls(
    inputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    accessibilityEnabled: Boolean,
    onSetUpAccessibility: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    todayLine: @Composable () -> Unit = {}
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        todayLine()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.input_mode_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                InputModeSelector(
                    selected = inputMode,
                    onSelect = onInputModeChange,
                    accessibilityEnabled = accessibilityEnabled,
                    onSetUpAccessibility = onSetUpAccessibility
                )
            }
        }
        StartStopButton(
            isActive = false,
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
    }
}

/** Minimal, near-black layout with a dimmed window: the whole screen counts. */
@Composable
private fun FocusContent(session: SessionUi, onExit: () -> Unit) {
    val state = session.state()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FocusBackground)
    ) {
        TapToCountPad(
            enabled = session.tapEnabled,
            countDescription = countDescription(state),
            onCount = session.onCount,
            shape = RectangleShape,
            containerColor = Color.Transparent,
            pressedColor = FocusText,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 64.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CounterDisplay(
                    state = state,
                    isDarkTheme = true,
                    footer = session.roundsText,
                    dimmed = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
                val mantra = state.currentMantra
                Text(
                    text = if (state.isPaused) {
                        stringResource(R.string.status_paused)
                    } else if (mantra != null && !state.isGoalComplete) {
                        "${mantra.name} · " + pluralStringResource(
                            R.plurals.goal_mantra_progress,
                            mantra.targetRounds,
                            state.mantraRoundsCompleted,
                            mantra.targetRounds
                        )
                    } else {
                        session.statusText
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusText.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.focus_exit),
                    tint = FocusText
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            HoldToUndoButton(enabled = session.canUndo, onUndo = session.onUndo, dimmed = true)
        }
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
