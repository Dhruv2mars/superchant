package com.superchant.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.superchant.R
import com.superchant.data.Backup
import com.superchant.data.BackupCodec
import com.superchant.data.BackupFormatException
import com.superchant.data.PreferencesRepository
import com.superchant.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Export and import of all data as a JSON file the user picks through the system file picker, so
 * history and goals can be kept safe or moved to a new phone without any account or network.
 */
@Composable
internal fun BackupControls(
    preferencesRepository: PreferencesRepository,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun report(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME_TYPE)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val message = try {
                val backup = preferencesRepository.exportBackup(System.currentTimeMillis())
                writeText(context, uri, BackupCodec.encode(backup))
                resources.getQuantityString(R.plurals.backup_exported, backup.history.size, backup.history.size, backup.goals.size)
            } catch (e: Exception) {
                resources.getString(R.string.backup_export_failed)
            }
            busy = false
            report(message)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val message = try {
                val backup = BackupCodec.decode(readText(context, uri))
                val result = preferencesRepository.importBackup(backup)
                // The backup may turn the reminder on or move it
                ReminderScheduler.sync(context.applicationContext)
                resources.getQuantityString(
                    R.plurals.backup_imported, result.sessionsAdded, result.sessionsAdded, result.goalsAdded + result.goalsUpdated
                )
            } catch (e: BackupFormatException) {
                resources.getString(R.string.backup_not_valid)
            } catch (e: Exception) {
                resources.getString(R.string.backup_import_failed)
            }
            busy = false
            report(message)
        }
    }

    Column {
        Text(
            text = stringResource(R.string.backup_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch(BackupCodec.suggestedFileName(LocalDate.now())) },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_export))
            }
            OutlinedButton(
                // Some file managers label .json files as plain text or binary
                onClick = { importLauncher.launch(arrayOf(Backup.MIME_TYPE, "text/*", "application/octet-stream")) },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_import))
            }
        }
    }
}

private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    // "wt" truncates, so overwriting a longer old backup leaves no trailing bytes
    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")
    stream.bufferedWriter().use { it.write(text) }
}

private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val stream = context.contentResolver.openInputStream(uri) ?: error("No input stream")
    // Backups are small; refuse anything absurd rather than running out of memory
    stream.use { input ->
        val bytes = input.readNBytesCompat(MAX_BACKUP_BYTES + 1)
        if (bytes.size > MAX_BACKUP_BYTES) throw BackupFormatException("Too large")
        bytes.decodeToString()
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
