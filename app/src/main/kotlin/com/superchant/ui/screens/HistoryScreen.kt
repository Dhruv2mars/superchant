package com.superchant.ui.screens

import android.content.res.Resources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.superchant.R
import com.superchant.data.HistoryStats
import com.superchant.data.PreferencesRepository
import com.superchant.data.SessionRecord
import com.superchant.ui.components.AppTopBar
import com.superchant.ui.components.BackButton
import com.superchant.ui.components.ConfirmDestructiveDialog
import com.superchant.data.bucketSessions
import com.superchant.data.millisUntilNextMidnight
import com.superchant.ui.components.EmptyState
import com.superchant.ui.components.InsightsCard
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(
    preferencesRepository: PreferencesRepository,
    onBackClick: () -> Unit
) {
    // null until loaded, so the empty state doesn't flash before the data arrives
    val loadedHistory by preferencesRepository.sessionHistory.collectAsStateWithLifecycle()
    val sessionHistory = loadedHistory ?: emptyList()
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val dailyTarget = preferencesRepository.dailyTarget.collectAsStateWithLifecycle().value ?: 0
    val stats = preferencesRepository.historyStats.collectAsStateWithLifecycle().value
    
    // Re-bucket when the day changes (at midnight while open, or on returning to the screen)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    LaunchedEffect(now) {
        delay(millisUntilNextMidnight(now) + 1_000)
        now = System.currentTimeMillis()
    }
    val categorizedSessions = remember(sessionHistory, now) {
        bucketSessions(sessionHistory, now)
    }
    // One pair for the whole list: building SimpleDateFormats per row (as rows scroll in) is costly
    val formats = remember { SessionFormats() }
    
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.history),
                navigationIcon = { BackButton(onBackClick) },
                actions = {
                    if (sessionHistory.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.history_clear)
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(
                visible = loadedHistory?.isEmpty() == true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.history_empty_title),
                    message = stringResource(R.string.history_empty_message),
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Waits for the stats too, so the cards above the list never pop in over it
            AnimatedVisibility(
                visible = sessionHistory.isNotEmpty() && stats != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary card
                    if (stats != null) {
                        item {
                            SummaryCard(stats = stats)
                        }
                        item {
                            InsightsCard(stats = stats, dailyTarget = dailyTarget)
                        }
                    }
                    
                    // Today section
                    if (categorizedSessions.today.isNotEmpty()) {
                        item {
                            SectionHeader(title = stringResource(R.string.history_today))
                        }
                        items(categorizedSessions.today, key = { it.id }) { session ->
                            SessionCard(session = session, showDate = false, formats = formats)
                        }
                    }
                    
                    // This Week section
                    if (categorizedSessions.thisWeek.isNotEmpty()) {
                        item {
                            SectionHeader(title = stringResource(R.string.history_this_week))
                        }
                        items(categorizedSessions.thisWeek, key = { it.id }) { session ->
                            SessionCard(session = session, showDate = true, formats = formats)
                        }
                    }
                    
                    // This Month section
                    if (categorizedSessions.thisMonth.isNotEmpty()) {
                        item {
                            SectionHeader(title = stringResource(R.string.history_this_month))
                        }
                        items(categorizedSessions.thisMonth, key = { it.id }) { session ->
                            SessionCard(session = session, showDate = true, formats = formats)
                        }
                    }
                    
                    // Older section
                    if (categorizedSessions.older.isNotEmpty()) {
                        item {
                            SectionHeader(title = stringResource(R.string.history_older))
                        }
                        items(categorizedSessions.older, key = { it.id }) { session ->
                            SessionCard(session = session, showDate = true, formats = formats)
                        }
                    }
                    
                    // Bottom spacing
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
    
    // Clear confirmation dialog
    if (showClearDialog) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.history_clear_title),
            text = stringResource(R.string.history_clear_message),
            confirmLabel = stringResource(R.string.history_clear_confirm),
            onConfirm = {
                preferencesRepository.write { clearSessionHistory() }
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SummaryCard(stats: HistoryStats) {
    val totalCounts = stats.totalCounts
    val totalRounds = stats.totalRounds
    val totalSessions = stats.sessions
    val totalDuration = stats.totalMillis
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.history_all_time_stats),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(value = formatNumber(totalCounts), label = stringResource(R.string.history_stat_counts))
                StatItem(value = formatNumber(totalRounds), label = stringResource(R.string.history_stat_rounds))
                StatItem(value = totalSessions.toString(), label = stringResource(R.string.history_stat_sessions))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))
            
            // Total time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.history_total_time, formatDurationLong(totalDuration, LocalResources.current)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Date and time formats for session rows; main thread only (SimpleDateFormat isn't thread-safe). */
private class SessionFormats {
    val date = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    val time = SimpleDateFormat("h:mm a", Locale.getDefault())
}

@Composable
private fun SessionCard(session: SessionRecord, showDate: Boolean, formats: SessionFormats) {
    val dateFormat = formats.date
    val timeFormat = formats.time
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top row with date/time and stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    if (showDate) {
                        Text(
                            text = dateFormat.format(Date(session.startTime)),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeFormat.format(Date(session.startTime)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (showDate) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = " • ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(session.durationMillis, LocalResources.current),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = session.totalCounts.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = pluralStringResource(R.plurals.counts_label, session.totalCounts),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = session.totalRounds.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = pluralStringResource(R.plurals.rounds_label, session.totalRounds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Goal indicator if present
            if (session.goalName != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = session.goalName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long, resources: Resources): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    
    return when {
        hours > 0 -> resources.getString(R.string.history_duration_hm, hours, minutes)
        minutes > 0 -> resources.getString(R.string.history_duration_ms, minutes, seconds)
        else -> resources.getString(R.string.history_duration_s, seconds)
    }
}

private fun formatDurationLong(millis: Long, resources: Resources): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = (TimeUnit.MILLISECONDS.toMinutes(millis) % 60).toInt()
    
    return when {
        hours > 0 -> resources.getString(R.string.history_duration_hm, hours, minutes)
        minutes > 0 -> resources.getQuantityString(R.plurals.minutes_count, minutes, minutes)
        else -> resources.getString(R.string.history_less_than_minute)
    }
}

private fun formatNumber(number: Int): String {
    return when {
        number >= 999_950 -> String.format(Locale.getDefault(), "%.1fM", number / 1_000_000.0)
        number >= 10_000 -> String.format(Locale.getDefault(), "%.1fK", number / 1_000.0)
        number >= 1_000 -> String.format(Locale.getDefault(), "%,d", number)
        else -> number.toString()
    }
}
