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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.superchant.R
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
    
    Scaffold(
        topBar = {
            AppTopBar(
                title = "History",
                navigationIcon = { BackButton(onBackClick) },
                actions = {
                    if (sessionHistory.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Clear history"
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
                    title = "No sessions yet",
                    message = "Your chanting history will appear here",
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            AnimatedVisibility(
                visible = sessionHistory.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary card
                    item {
                        SummaryCard(sessions = sessionHistory)
                    }
                    item {
                        InsightsCard(sessions = sessionHistory, dailyTarget = dailyTarget)
                    }
                    
                    // Today section
                    if (categorizedSessions.today.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Today")
                        }
                        items(categorizedSessions.today, key = { it.id }) { session ->
                            SessionCard(session = session, showDate = false)
                        }
                    }
                    
                    // This Week section
                    if (categorizedSessions.thisWeek.isNotEmpty()) {
                        item {
                            SectionHeader(title = "This Week")
                        }
                        items(categorizedSessions.thisWeek, key = { it.id }) { session ->
                            SessionCard(session = session, showDate = true)
                        }
                    }
                    
                    // This Month section
                    if (categorizedSessions.thisMonth.isNotEmpty()) {
                        item {
                            SectionHeader(title = "This Month")
                        }
                        items(categorizedSessions.thisMonth, key = { it.id }) { session ->
                            SessionCard(session = session, showDate = true)
                        }
                    }
                    
                    // Older section
                    if (categorizedSessions.older.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Older")
                        }
                        items(categorizedSessions.older, key = { it.id }) { session ->
                            SessionCard(session = session, showDate = true)
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
            title = "Clear History",
            text = "Are you sure you want to clear all session history? This cannot be undone.",
            confirmLabel = "Clear",
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
private fun SummaryCard(sessions: List<SessionRecord>) {
    val totalCounts = remember(sessions) { sessions.sumOf { it.totalCounts } }
    val totalRounds = remember(sessions) { sessions.sumOf { it.totalRounds } }
    val totalSessions = sessions.size
    val totalDuration = remember(sessions) { sessions.sumOf { it.durationMillis } }
    
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
                text = "All Time Stats",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(value = formatNumber(totalCounts), label = "Counts")
                StatItem(value = formatNumber(totalRounds), label = "Rounds")
                StatItem(value = totalSessions.toString(), label = "Sessions")
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
                    text = "Total time: ${formatDurationLong(totalDuration, LocalResources.current)}",
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

@Composable
private fun SessionCard(session: SessionRecord, showDate: Boolean) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    
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
                            text = formatDuration(session.durationMillis),
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

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun formatDurationLong(millis: Long, resources: Resources): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = (TimeUnit.MILLISECONDS.toMinutes(millis) % 60).toInt()
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> resources.getQuantityString(R.plurals.minutes_count, minutes, minutes)
        else -> "Less than a minute"
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
