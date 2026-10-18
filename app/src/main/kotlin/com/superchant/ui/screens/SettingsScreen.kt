package com.superchant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.superchant.BuildConfig
import com.superchant.R
import com.superchant.data.ChantState
import com.superchant.data.InputMode
import com.superchant.data.PreferencesRepository
import com.superchant.data.VibrationSettings
import com.superchant.service.VolumeKeyAccessibilityService
import com.superchant.ui.components.AppTopBar
import com.superchant.ui.components.BackButton
import com.superchant.ui.components.InputModeSelector
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
    // Loaded at app start, so the stored values (not defaults) are shown from the first frame
    val vibration = preferencesRepository.vibrationSettings.collectAsStateWithLifecycle().value ?: VibrationSettings()
    
    Scaffold(
        topBar = {
            AppTopBar(title = "Settings", navigationIcon = { BackButton(onBackClick) })
        },
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
                    onSetUpAccessibility = { VolumeKeyAccessibilityService.openAccessibilitySettings(context) }
                )
            }
            
            // Appearance section
            SettingsSection(title = "Appearance") {
                SettingsItem(
                    title = "Theme",
                    subtitle = when (themeMode) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.SYSTEM -> "System"
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
            SettingsSection(title = "Vibration") {
                // Count vibration toggle
                SettingsToggleItem(
                    title = "Vibrate on each count",
                    subtitle = "Quick haptic feedback for every button press",
                    checked = vibration.countEnabled,
                    onCheckedChange = { enabled ->
                        preferencesRepository.write { setCountVibrationEnabled(enabled) }
                    }
                )
                
                // Count vibration intensity slider
                SettingsSliderItem(
                    title = "Count intensity",
                    value = vibration.countIntensity,
                    enabled = vibration.countEnabled,
                    onValueChangeFinished = { intensity ->
                        preferencesRepository.write { setCountVibrationIntensity(intensity) }
                    }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                // Round complete vibration toggle
                SettingsToggleItem(
                    title = "Vibrate on round complete",
                    subtitle = "Stronger vibration when you finish ${ChantState.COUNTS_PER_ROUND} counts",
                    checked = vibration.roundEnabled,
                    onCheckedChange = { enabled ->
                        preferencesRepository.write { setVibrationEnabled(enabled) }
                    }
                )
                
                // Round vibration intensity slider
                SettingsSliderItem(
                    title = "Round intensity",
                    value = vibration.roundIntensity,
                    enabled = vibration.roundEnabled,
                    onValueChangeFinished = { intensity ->
                        preferencesRepository.write { setRoundVibrationIntensity(intensity) }
                    }
                )
            }
            
            // About section
            SettingsSection(title = "About") {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SuperChant",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "A distraction-free digital mala for your spiritual practice",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

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
    onCheckedChange: (Boolean) -> Unit
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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
    val subtitle = getIntensityLabel(dragValue.roundToInt())
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

private fun getIntensityLabel(intensity: Int): String {
    return when {
        intensity <= 50 -> "Very Light"
        intensity <= 100 -> "Light"
        intensity <= 150 -> "Medium"
        intensity <= 200 -> "Strong"
        else -> "Very Strong"
    }
}
