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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.superchant.BuildConfig
import com.superchant.R
import com.superchant.data.InputMode
import com.superchant.data.PreferencesRepository
import com.superchant.service.VolumeKeyAccessibilityService
import com.superchant.ui.components.AppTopBar
import com.superchant.ui.components.BackButton
import com.superchant.ui.components.InputModeSelector
import com.superchant.ui.components.ThemeToggle
import com.superchant.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    preferencesRepository: PreferencesRepository,
    themeMode: ThemeMode,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val vibrationEnabled by preferencesRepository.vibrationEnabledFlow.collectAsState(initial = true)
    val countVibrationEnabled by preferencesRepository.countVibrationEnabledFlow.collectAsState(initial = true)
    val countVibrationIntensity by preferencesRepository.countVibrationIntensityFlow.collectAsState(initial = 80)
    val roundVibrationIntensity by preferencesRepository.roundVibrationIntensityFlow.collectAsState(initial = 255)
    val context = LocalContext.current
    val savedInputMode by preferencesRepository.inputModeFlow.collectAsState(initial = null)
    val accessibilityEnabled = remember { VolumeKeyAccessibilityService.isAccessibilityServiceEnabled(context) }
    
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
                    onSelect = { mode -> scope.launch { preferencesRepository.setInputMode(mode) } },
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
                            scope.launch {
                                preferencesRepository.setThemeMode(newMode)
                            }
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
                    checked = countVibrationEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferencesRepository.setCountVibrationEnabled(enabled)
                        }
                    }
                )
                
                // Count vibration intensity slider
                SettingsSliderItem(
                    title = "Count intensity",
                    subtitle = getIntensityLabel(countVibrationIntensity),
                    value = countVibrationIntensity.toFloat(),
                    valueRange = 1f..255f,
                    enabled = countVibrationEnabled,
                    onValueChange = { intensity ->
                        scope.launch {
                            preferencesRepository.setCountVibrationIntensity(intensity.roundToInt())
                        }
                    }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                // Round complete vibration toggle
                SettingsToggleItem(
                    title = "Vibrate on round complete",
                    subtitle = "Stronger vibration when you finish 108 counts",
                    checked = vibrationEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferencesRepository.setVibrationEnabled(enabled)
                        }
                    }
                )
                
                // Round vibration intensity slider
                SettingsSliderItem(
                    title = "Round intensity",
                    subtitle = getIntensityLabel(roundVibrationIntensity),
                    value = roundVibrationIntensity.toFloat(),
                    valueRange = 1f..255f,
                    enabled = vibrationEnabled,
                    onValueChange = { intensity ->
                        scope.launch {
                            preferencesRepository.setRoundVibrationIntensity(intensity.roundToInt())
                        }
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

@Composable
private fun SettingsSliderItem(
    title: String,
    subtitle: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
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
            if (subtitle != null) {
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
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
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
