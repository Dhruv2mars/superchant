package com.superchant.ui.screens

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.superchant.BuildConfig
import com.superchant.R
import com.superchant.data.InputMode
import com.superchant.data.CountingFeel
import com.superchant.data.PreferencesRepository
import com.superchant.data.ReminderSettings
import com.superchant.data.RoundSize
import com.superchant.data.VibrationSettings
import com.superchant.service.VolumeKeyAccessibilityService
import com.superchant.ui.components.AccessibilitySetupDialog
import com.superchant.ui.components.AppTopBar
import com.superchant.ui.components.BackButton
import com.superchant.ui.components.InputModeSelector
import com.superchant.ui.components.RoundSizePicker
import com.superchant.ui.components.ThemeToggle
import com.superchant.ui.theme.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    preferencesRepository: PreferencesRepository,
    themeMode: ThemeMode,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val savedInputMode by preferencesRepository.inputModeFlow.collectAsStateWithLifecycle(initialValue = null)
    // Re-checked on resume, e.g. after returning from the accessibility settings
    var accessibilityEnabled by remember { mutableStateOf(VolumeKeyAccessibilityService.isAccessibilityServiceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        accessibilityEnabled = VolumeKeyAccessibilityService.isAccessibilityServiceEnabled(context)
        onPauseOrDispose { }
    }
    val hasAmplitudeControl = remember { context.vibrator().hasAmplitudeControl() }
    // Loaded at app start, so the stored values (not defaults) are shown from the first frame
    val vibration = preferencesRepository.vibrationSettings.collectAsStateWithLifecycle().value ?: VibrationSettings()
    val roundSize = preferencesRepository.roundSize.collectAsStateWithLifecycle().value ?: RoundSize.DEFAULT
    val feel = preferencesRepository.countingFeel.collectAsStateWithLifecycle().value ?: CountingFeel()
    val dailyTarget = preferencesRepository.dailyTarget.collectAsStateWithLifecycle().value ?: 0
    val reminder = preferencesRepository.reminder.collectAsStateWithLifecycle().value ?: ReminderSettings()
    val accessibilityConsent = preferencesRepository.accessibilityConsent.collectAsStateWithLifecycle().value == true
    var showAccessibilitySetup by remember { mutableStateOf(false) }
    if (showAccessibilitySetup) {
        AccessibilitySetupDialog(
            consented = accessibilityConsent,
            onConsent = { preferencesRepository.write { setAccessibilityConsent(true) } },
            onDismiss = { showAccessibilitySetup = false },
            onOpenSettings = {
                showAccessibilitySetup = false
                VolumeKeyAccessibilityService.openAccessibilitySettings(context)
            }
        )
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.settings), navigationIcon = { BackButton(onBackClick) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(title = stringResource(R.string.settings_counting)) {
                InputModeSelector(
                    selected = savedInputMode ?: InputMode.default(accessibilityEnabled),
                    onSelect = { mode -> preferencesRepository.write { setInputMode(mode) } },
                    accessibilityEnabled = accessibilityEnabled,
                    onSetUpAccessibility = { showAccessibilitySetup = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = stringResource(R.string.round_size_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.round_size_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RoundSizePicker(
                    selected = roundSize,
                    onSelect = { size -> if (size != null) preferencesRepository.write { setRoundSize(size) } }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsToggleItem(
                    title = stringResource(R.string.pocket_guard_title),
                    subtitle = stringResource(R.string.pocket_guard_desc),
                    checked = feel.pocketGuard,
                    onCheckedChange = { enabled -> preferencesRepository.write { setPocketGuard(enabled) } }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleItem(
                    title = stringResource(R.string.bead_click_title),
                    subtitle = stringResource(R.string.bead_click_desc),
                    checked = feel.beadClick,
                    onCheckedChange = { enabled -> preferencesRepository.write { setBeadClick(enabled) } }
                )
            }
            
            SettingsSection(title = stringResource(R.string.settings_practice)) {
                DailyTargetItem(
                    target = dailyTarget,
                    onChange = { rounds -> preferencesRepository.write { setDailyTarget(rounds) } }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                ReminderControls(
                    preferencesRepository = preferencesRepository,
                    settings = reminder,
                    snackbarHostState = snackbarHostState
                )
            }
            
            // Appearance section
            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                SettingsItem(
                    title = stringResource(R.string.settings_theme),
                    subtitle = when (themeMode) {
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    }
                ) {
                    ThemeToggle(
                        currentMode = themeMode,
                        onModeSelected = { newMode ->
                            preferencesRepository.write { setThemeMode(newMode) }
                        }
                    )
                }
            }
            
            // Feedback section
            SettingsSection(title = stringResource(R.string.settings_vibration)) {
                // Count vibration toggle
                SettingsToggleItem(
                    title = stringResource(R.string.settings_vibrate_count_title),
                    subtitle = stringResource(R.string.settings_vibrate_count_desc),
                    checked = vibration.countEnabled,
                    onCheckedChange = { enabled ->
                        preferencesRepository.write { setCountVibrationEnabled(enabled) }
                    }
                )
                
                // Count vibration intensity slider (only where the vibrator can vary its strength)
                if (hasAmplitudeControl) {
                    SettingsSliderItem(
                        title = stringResource(R.string.settings_count_intensity),
                        value = vibration.countIntensity,
                        enabled = vibration.countEnabled,
                        onValueChangeFinished = { intensity ->
                            preferencesRepository.write { setCountVibrationIntensity(intensity) }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                SettingsToggleItem(
                    title = stringResource(R.string.milestone_pulse_title),
                    subtitle = stringResource(R.string.milestone_pulse_desc),
                    checked = feel.milestonePulse && vibration.countEnabled,
                    enabled = vibration.countEnabled,
                    onCheckedChange = { enabled -> preferencesRepository.write { setMilestonePulse(enabled) } }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                // Round complete vibration toggle
                SettingsToggleItem(
                    title = stringResource(R.string.settings_vibrate_round_title),
                    subtitle = stringResource(R.string.settings_vibrate_round_desc),
                    checked = vibration.roundEnabled,
                    onCheckedChange = { enabled ->
                        preferencesRepository.write { setVibrationEnabled(enabled) }
                    }
                )
                
                // Round vibration intensity slider
                if (hasAmplitudeControl) {
                    SettingsSliderItem(
                        title = stringResource(R.string.settings_round_intensity),
                        value = vibration.roundIntensity,
                        enabled = vibration.roundEnabled,
                        onValueChangeFinished = { intensity ->
                            preferencesRepository.write { setRoundVibrationIntensity(intensity) }
                        }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.vibration_strength_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            
            SettingsSection(title = stringResource(R.string.backup_title)) {
                BackupControls(preferencesRepository = preferencesRepository, snackbarHostState = snackbarHostState)
            }
            
            // About section
            SettingsSection(title = stringResource(R.string.settings_about)) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Rounds per day, stepped through common values; 0 is off. */
@Composable
private fun DailyTargetItem(target: Int, onChange: (Int) -> Unit) {
    // The stepper shares the title's line and the description spans the full width, so it fits narrow screens
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.daily_target_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = { onChange(DAILY_TARGET_STEPS.lastOrNull { it < target } ?: 0) },
                enabled = target > 0
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.daily_target_decrease))
            }
            Text(
                text = if (target > 0) pluralStringResource(R.plurals.rounds_count, target, target) else stringResource(R.string.daily_target_off),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 84.dp)
            )
            FilledTonalIconButton(
                onClick = { onChange(DAILY_TARGET_STEPS.firstOrNull { it > target } ?: target) },
                enabled = target < DAILY_TARGET_STEPS.last()
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.daily_target_increase))
            }
        }
        Text(
            text = stringResource(R.string.daily_target_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val DAILY_TARGET_STEPS = (1..16).toList() + listOf(20, 24, 27, 32, 48, 54, 64, 108)

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    // The whole row toggles, so the target is large and TalkBack reads it as one switch
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

/**
 * Vibration intensity slider (1-255). The drag position is local state; [onValueChangeFinished]
 * is called once when the drag ends, so a drag is one write instead of one per frame.
 */
@Composable
private fun SettingsSliderItem(
    title: String,
    value: Int,
    enabled: Boolean = true,
    onValueChangeFinished: (Int) -> Unit
) {
    var dragValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val subtitle = stringResource(intensityLabel(dragValue.roundToInt()))
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                }
            )
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onValueChangeFinished(dragValue.roundToInt()) },
            valueRange = 1f..255f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
                disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledActiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            ),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun Context.vibrator(): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

@StringRes
private fun intensityLabel(intensity: Int): Int {
    return when {
        intensity <= 50 -> R.string.settings_intensity_very_light
        intensity <= 100 -> R.string.settings_intensity_light
        intensity <= 150 -> R.string.settings_intensity_medium
        intensity <= 200 -> R.string.settings_intensity_strong
        else -> R.string.settings_intensity_very_strong
    }
}
