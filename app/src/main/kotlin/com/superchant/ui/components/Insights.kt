package com.superchant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.superchant.R
import com.superchant.data.DayTotal
import com.superchant.data.MantraTotal
import com.superchant.data.PracticeStats
import com.superchant.data.SessionRecord
import com.superchant.data.Streak
import com.superchant.data.millisUntilNextMidnight
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Today's date, updated at midnight and whenever the screen resumes. */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    LaunchedEffect(today) {
        delay(millisUntilNextMidnight(System.currentTimeMillis()) + 1_000)
        today = LocalDate.now()
    }
    return today
}

/** Streak, a practice heatmap and per-mantra lifetime totals, derived from [sessions]. */
@Composable
fun InsightsCard(
    sessions: List<SessionRecord>,
    dailyTarget: Int,
    modifier: Modifier = Modifier
) {
    val today = rememberToday()
    val daily = remember(sessions) { PracticeStats.dailyTotals(sessions) }
    val streak = remember(daily, today, dailyTarget) { PracticeStats.streak(daily, today, dailyTarget) }
    val mantras = remember(sessions) { PracticeStats.mantraTotals(sessions) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.insights_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            StreakRow(streak = streak, today = daily[today], dailyTarget = dailyTarget)
            Spacer(modifier = Modifier.height(16.dp))
            PracticeHeatmap(daily = daily, today = today, dailyTarget = dailyTarget)
            if (mantras.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                MantraTotals(mantras)
            }
        }
    }
}

@Composable
private fun StreakRow(streak: Streak, today: DayTotal?, dailyTarget: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        InsightStat(
            value = streak.current.toString(),
            label = pluralStringResource(R.plurals.insights_streak_days, streak.current)
        )
        InsightStat(
            value = streak.best.toString(),
            label = stringResource(R.string.insights_best_streak)
        )
        val roundsToday = today?.rounds ?: 0
        InsightStat(
            value = if (dailyTarget > 0) "$roundsToday/$dailyTarget" else roundsToday.toString(),
            label = stringResource(R.string.insights_rounds_today)
        )
    }
}

@Composable
private fun InsightStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The last [weeks] weeks as a grid: one column per week (starting on the locale's first day), one
 * row per weekday, shaded by rounds. TalkBack reads a one-line summary instead of 100+ cells.
 */
@Composable
private fun PracticeHeatmap(
    daily: Map<LocalDate, DayTotal>,
    today: LocalDate,
    dailyTarget: Int,
    weeks: Int = 18
) {
    val firstDayOfWeek: DayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val start = remember(today, firstDayOfWeek) {
        today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).minusWeeks((weeks - 1).toLong())
    }
    val days = remember(start, today) { generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList() }
    val reference = remember(daily, days, dailyTarget) {
        if (dailyTarget > 0) dailyTarget else days.maxOfOrNull { daily[it]?.rounds ?: 0 }?.coerceAtLeast(1) ?: 1
    }
    val practiced = days.count { (daily[it]?.counts ?: 0) > 0 }
    val summary = pluralStringResource(R.plurals.insights_heatmap_summary, days.size, practiced, days.size)

    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val shade = MaterialTheme.colorScheme.primary
    val levels = listOf(empty, shade.copy(alpha = 0.3f), shade.copy(alpha = 0.5f), shade.copy(alpha = 0.75f), shade)

    Column(modifier = Modifier.clearAndSetSemantics { contentDescription = summary }) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 3.dp
            val cell = ((maxWidth - gap * (weeks - 1)) / weeks).coerceAtMost(16.dp)
            Canvas(modifier = Modifier.size(width = maxWidth, height = cell * 7 + gap * 6)) {
                val cellPx = cell.toPx()
                val step = cellPx + gap.toPx()
                val offsetX = (size.width - (step * weeks - gap.toPx())) / 2
                days.forEachIndexed { index, day ->
                    val level = PracticeStats.heatLevel(daily[day], reference)
                    drawRoundRect(
                        color = levels[level],
                        topLeft = Offset(offsetX + (index / 7) * step, (index % 7) * step),
                        size = Size(cellPx, cellPx),
                        cornerRadius = CornerRadius(cellPx * 0.25f)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            HeatLegend(levels)
        }
    }
}

@Composable
private fun HeatLegend(levels: List<Color>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.insights_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        levels.forEach { color ->
            Spacer(modifier = Modifier.width(3.dp))
            Canvas(modifier = Modifier.size(10.dp)) {
                drawRoundRect(color = color, cornerRadius = CornerRadius(size.minDimension * 0.25f))
            }
        }
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.insights_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MantraTotals(mantras: List<MantraTotal>) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.insights_mantras_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    val shown = if (showAll) mantras else mantras.take(COLLAPSED_MANTRAS)
    shown.forEach { mantra ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mantra.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = pluralStringResource(R.plurals.rounds_count, mantra.rounds, mantra.rounds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp)
            )
            Text(
                text = pluralStringResource(R.plurals.insights_counts, mantra.counts, mantra.counts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
    if (mantras.size > COLLAPSED_MANTRAS) {
        TextButton(onClick = { showAll = !showAll }) {
            Text(stringResource(if (showAll) R.string.insights_show_less else R.string.insights_show_all))
        }
    }
}

private const val COLLAPSED_MANTRAS = 5

/** One quiet line for Home: today's rounds (against the target, if set) and the current streak. */
@Composable
fun TodayLine(
    sessions: List<SessionRecord>,
    dailyTarget: Int,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty() && dailyTarget == 0) return
    val today = rememberToday()
    val daily = remember(sessions) { PracticeStats.dailyTotals(sessions) }
    val streak = remember(daily, today, dailyTarget) { PracticeStats.streak(daily, today, dailyTarget) }
    val rounds = daily[today]?.rounds ?: 0
    val todayText = if (dailyTarget > 0) {
        pluralStringResource(R.plurals.today_rounds_of_target, dailyTarget, rounds, dailyTarget)
    } else {
        pluralStringResource(R.plurals.today_rounds, rounds, rounds)
    }
    val text = if (streak.current > 0) {
        todayText + " · " + pluralStringResource(R.plurals.streak_days, streak.current, streak.current)
    } else {
        todayText
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (streak.todayDone && dailyTarget > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (dailyTarget > 0) {
            LinearProgressIndicator(
                progress = { (rounds.toFloat() / dailyTarget).coerceIn(0f, 1f) },
                drawStopIndicator = {},
                modifier = Modifier
                    .padding(top = 6.dp)
                    .width(120.dp)
            )
        }
    }
}
