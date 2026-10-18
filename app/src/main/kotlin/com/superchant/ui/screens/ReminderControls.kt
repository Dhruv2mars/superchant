package com.superchant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.superchant.R
import com.superchant.data.PreferencesRepository
import com.superchant.data.ReminderSettings
import com.superchant.reminder.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The optional daily reminder: a switch and a time. Turning it on asks for the notification
 * permission where needed; without it the reminder stays off and says why.
 */
@Composable
internal fun ReminderControls(
    preferencesRepository: PreferencesRepository,
    settings: ReminderSettings,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val needsMessage = stringResource(R.string.reminder_needs_notifications)

    fun apply(newSettings: ReminderSettings) {
        preferencesRepository.write { setReminder(newSettings) }
        if (newSettings.enabled) ReminderScheduler.schedule(context, newSettings.minuteOfDay) else ReminderScheduler.cancel(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) apply(settings.copy(enabled = true)) else scope.launch { snackbarHostState.showSnackbar(needsMessage) }
    }

    fun setEnabled(enabled: Boolean) {
        val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else apply(settings.copy(enabled = enabled))
    }

    val is24Hour = DateFormat.is24HourFormat(context)
    val timeText = remember(settings.minuteOfDay, is24Hour) {
        LocalTime.of(settings.minuteOfDay / 60, settings.minuteOfDay % 60)
            .format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a"))
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = settings.enabled, role = Role.Switch, onValueChange = ::setEnabled),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.reminder_setting_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.reminder_setting_desc, timeText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = null)
        }
        if (settings.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = stringResource(R.string.reminder_time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showTimePicker = true }) {
                    Text(timeText, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showTimePicker) {
        ReminderTimeDialog(
            minuteOfDay = settings.minuteOfDay,
            is24Hour = is24Hour,
            onConfirm = { minute ->
                showTimePicker = false
                apply(settings.copy(minuteOfDay = minute))
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(minuteOfDay: Int, is24Hour: Boolean, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
