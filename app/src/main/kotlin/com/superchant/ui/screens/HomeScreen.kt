package com.superchant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.superchant.R
import com.superchant.data.ChantState
import com.superchant.data.Goal
import com.superchant.service.ChantingService
import com.superchant.service.VolumeKeyAccessibilityService
import com.superchant.ui.components.AppTopBar
import com.superchant.ui.components.CounterDisplay
import com.superchant.ui.components.RoundsDisplay
import com.superchant.ui.components.StartStopButton
import com.superchant.ui.theme.ThemeMode

@Composable
fun HomeScreen(
    themeMode: ThemeMode,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onGoalsClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val chantState by ChantingService.chantStateFlow.collectAsState()
    
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Track if service is actually running
    var isServiceRunning by remember { mutableStateOf(ChantingService.isRunning) }
    
    // Track accessibility service enabled state
    var isAccessibilityEnabled by remember { 
        mutableStateOf(VolumeKeyAccessibilityService.isAccessibilityServiceEnabled(context)) 
    }
    
    // Show setup dialog when user tries to start without accessibility
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    
    // Update service running state
    LaunchedEffect(chantState) {
        isServiceRunning = ChantingService.isRunning
    }
    
    // Re-check accessibility when returning to app (user might have enabled it in settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = VolumeKeyAccessibilityService.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Permission handling for notifications (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted && isAccessibilityEnabled) {
            ChantingService.start(context)
            isServiceRunning = true
        }
    }
    
    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    
    // Common button click handler
    val onStartStopClick: () -> Unit = {
        if (isServiceRunning) {
            ChantingService.stop(context)
            isServiceRunning = false
        } else {
            // First check accessibility permission
            if (!isAccessibilityEnabled) {
                showAccessibilityDialog = true
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                // Then check notification permission (Android 13+)
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // All permissions granted, start the service
                ChantingService.start(context)
                isServiceRunning = true
            }
        }
    }
    
    // Accessibility Setup Dialog
    if (showAccessibilityDialog) {
        AccessibilitySetupDialog(
            onDismiss = { showAccessibilityDialog = false },
            onOpenSettings = {
                showAccessibilityDialog = false
                VolumeKeyAccessibilityService.openAccessibilitySettings(context)
            }
        )
    }
    
    Scaffold(
        topBar = {
            AppTopBar(
                title = "SuperChant",
                actions = {
                    IconButton(onClick = onGoalsClick) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = "Goals"
                        )
                    }
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "History"
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        if (isLandscape) {
            // Landscape layout: horizontal arrangement
            LandscapeContent(
                chantState = chantState,
                isServiceRunning = isServiceRunning,
                isAccessibilityEnabled = isAccessibilityEnabled,
                isDarkTheme = isDarkTheme,
                onStartStopClick = onStartStopClick,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            // Portrait layout: vertical arrangement with scrolling
            PortraitContent(
                chantState = chantState,
                isServiceRunning = isServiceRunning,
                isAccessibilityEnabled = isAccessibilityEnabled,
                isDarkTheme = isDarkTheme,
                onStartStopClick = onStartStopClick,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun PortraitContent(
    chantState: ChantState,
    isServiceRunning: Boolean,
    isAccessibilityEnabled: Boolean,
    isDarkTheme: Boolean,
    onStartStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        
        // Goal progress display (when goal is active)
        AnimatedVisibility(
            visible = isServiceRunning && chantState.activeGoal != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            chantState.activeGoal?.let { goal ->
                GoalProgressDisplay(
                    goal = goal,
                    currentMantraIndex = chantState.currentMantraIndex,
                    mantraRoundsCompleted = chantState.mantraRoundsCompleted,
                    isGoalComplete = chantState.isGoalComplete
                )
            }
        }
        
        // Counter display
        CounterDisplay(
            state = if (isServiceRunning) chantState else ChantState(),
            isDarkTheme = isDarkTheme,
            modifier = Modifier
                .fillMaxWidth()
        )
        
        // Rounds display (for current mantra in goal mode, or total in free mode)
        AnimatedVisibility(
            visible = isServiceRunning && (chantState.rounds > 0 || chantState.mantraRoundsCompleted > 0),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            if (chantState.activeGoal != null) {
                RoundsDisplay(
                    rounds = chantState.mantraRoundsCompleted,
                    label = "rounds this mantra"
                )
            } else {
                RoundsDisplay(
                    rounds = chantState.rounds
                )
            }
        }
        
        // Status text when active
        AnimatedVisibility(
            visible = isServiceRunning,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                SessionStatusText(isPaused = chantState.isPaused)
            }
        }
        
        AccessibilityHint(
            visible = !isServiceRunning && !isAccessibilityEnabled,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Start/Stop button
        StartStopButton(
            isActive = isServiceRunning,
            onClick = onStartStopClick,
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }
}

@Composable
private fun LandscapeContent(
    chantState: ChantState,
    isServiceRunning: Boolean,
    isAccessibilityEnabled: Boolean,
    isDarkTheme: Boolean,
    onStartStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Counter display on the left
        CounterDisplay(
            state = if (isServiceRunning) chantState else ChantState(),
            isDarkTheme = isDarkTheme,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 16.dp)
        )
        
        // Controls on the right
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Rounds display
            AnimatedVisibility(
                visible = isServiceRunning && chantState.rounds > 0,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                RoundsDisplay(
                    rounds = chantState.rounds
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status text when active
            AnimatedVisibility(
                visible = isServiceRunning,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SessionStatusText(isPaused = chantState.isPaused)
            }
            
            AccessibilityHint(
                visible = !isServiceRunning && !isAccessibilityEnabled,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Start/Stop button
            StartStopButton(
                isActive = isServiceRunning,
                onClick = onStartStopClick,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}

@Composable
private fun SessionStatusText(isPaused: Boolean) {
    Text(
        text = if (isPaused) "Paused" else "Press volume button to count",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** Shown when accessibility is not enabled and no session is running. */
@Composable
private fun AccessibilityHint(visible: Boolean, modifier: Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = "Tap Start, then enable the accessibility service to begin counting",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
    }
}

@Composable
private fun AccessibilitySetupDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val needsRestrictedSettings = remember {
        VolumeKeyAccessibilityService.needsRestrictedSettingsPermission(context)
    }
    
    if (needsRestrictedSettings) {
        // Android 13+ sideloaded app - show two-step flow
        RestrictedSettingsDialog(
            onDismiss = onDismiss,
            onOpenAppInfo = {
                VolumeKeyAccessibilityService.openAppSettings(context)
            },
            onOpenAccessibility = onOpenSettings
        )
    } else {
        // Standard flow - just open accessibility settings
        SimpleAccessibilityDialog(
            onDismiss = onDismiss,
            onOpenSettings = onOpenSettings
        )
    }
}

@Composable
private fun SimpleAccessibilityDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.accessibility_setup_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.accessibility_setup_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.accessibility_setup_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RestrictedSettingsDialog(
    onDismiss: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.restricted_settings_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.restricted_settings_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Primary action - Open Accessibility Settings
                SetupStepCard(
                    title = stringResource(R.string.restricted_settings_step2),
                    description = stringResource(R.string.restricted_settings_step2_desc),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    titleColor = Color.Unspecified
                ) {
                    Button(
                        onClick = onOpenAccessibility,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.enable_accessibility),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Secondary info - If toggle is blocked
                SetupStepCard(
                    title = stringResource(R.string.restricted_settings_step1),
                    description = stringResource(R.string.restricted_settings_step1_desc),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    titleColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    OutlinedButton(
                        onClick = onOpenAppInfo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.open_app_info),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SetupStepCard(
    title: String,
    description: String,
    containerColor: Color,
    titleColor: Color,
    action: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = titleColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            action()
        }
    }
}

@Composable
private fun GoalProgressDisplay(
    goal: Goal,
    currentMantraIndex: Int,
    mantraRoundsCompleted: Int,
    isGoalComplete: Boolean
) {
    Card(
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
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isGoalComplete) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Scrollable mantras list with max height (for goals with many mantras)
            val needsScroll = goal.mantras.size > 4
            Column(
                modifier = if (needsScroll) {
                    Modifier
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier
                }
            ) {
                // Show all mantras with progress
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
                                    contentDescription = "Completed",
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
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mantra.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrentMantra) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isCurrentMantra) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            if (isCurrentMantra && !isCompleted) {
                                Text(
                                    text = "$mantraRoundsCompleted / ${mantra.targetRounds} rounds",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "${mantra.targetRounds} rounds",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Show scroll hint if needed
            if (needsScroll) {
                Text(
                    text = "Scroll to see all mantras",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
