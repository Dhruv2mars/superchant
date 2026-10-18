package com.superchant.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything chanted on one local day. */
data class DayTotal(
    val counts: Int = 0,
    val rounds: Int = 0,
    val millis: Long = 0,
    val sessions: Int = 0
)

/** Consecutive practice days: [current] ends today, or yesterday while today isn't done yet. */
data class Streak(val current: Int, val best: Int, val todayDone: Boolean)

/** Lifetime total for a mantra, merged across goals by name. */
data class MantraTotal(val name: String, val rounds: Int, val counts: Int)

/**
 * Insights derived from session history. Pure functions, so they're computed off the stored records
 * (the single source of truth) and unit tested.
 */
object PracticeStats {

    /** Totals per local day; a session belongs to the day it started. */
    fun dailyTotals(sessions: List<SessionRecord>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, DayTotal> =
        sessions.groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
            .mapValues { (_, day) ->
                DayTotal(
                    counts = day.sumOf { it.totalCounts },
                    rounds = day.sumOf { it.totalRounds },
                    millis = day.sumOf { it.durationMillis },
                    sessions = day.size
                )
            }

    /** A day counts towards the streak once it meets the daily target (rounds), or has any counts without one. */
    fun isPracticeDay(total: DayTotal?, dailyTarget: Int): Boolean =
        total != null && if (dailyTarget > 0) total.rounds >= dailyTarget else total.counts > 0

    fun streak(daily: Map<LocalDate, DayTotal>, today: LocalDate, dailyTarget: Int): Streak {
        fun practiced(day: LocalDate) = isPracticeDay(daily[day], dailyTarget)
        val todayDone = practiced(today)
        var current = 0
        var day = if (todayDone) today else today.minusDays(1)
        while (practiced(day)) {
            current++
            day = day.minusDays(1)
        }
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (practiceDay in daily.keys.filter(::practiced).sorted()) {
            run = if (previous != null && previous.plusDays(1) == practiceDay) run + 1 else 1
            best = maxOf(best, run)
            previous = practiceDay
        }
        return Streak(current = current, best = maxOf(best, current), todayDone = todayDone)
    }

    /** Lifetime per-mantra totals, most chanted first. Names match ignoring case and surrounding spaces. */
    fun mantraTotals(sessions: List<SessionRecord>): List<MantraTotal> =
        sessions.flatMap { it.mantras }
            .filter { it.name.isNotBlank() }
            .groupBy { it.name.trim().lowercase() }
            .map { (_, tallies) -> MantraTotal(tallies.first().name.trim(), tallies.sumOf { it.rounds }, tallies.sumOf { it.counts }) }
            .sortedWith(compareByDescending<MantraTotal> { it.counts }.thenBy { it.name.lowercase() })

    /**
     * Heatmap shade 0..4: 0 for no practice, then four steps up to [reference] rounds (the daily target,
     * or the busiest day shown). A day with counts but no full round still shows the lightest shade.
     */
    fun heatLevel(total: DayTotal?, reference: Int): Int {
        if (total == null || total.counts <= 0) return 0
        val ratio = if (reference > 0) total.rounds.toFloat() / reference else 1f
        return (1 + (ratio.coerceIn(0f, 1f) * 3).toInt()).coerceIn(1, 4)
    }

    const val DAILY_TARGET_MAX = 200
}
