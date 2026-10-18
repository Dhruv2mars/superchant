package com.superchant.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.SortedMap
import java.util.TreeMap

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
 * Everything the stats views show, derived from the whole history once per change (off the main thread)
 * instead of in composition, where it reran on every visit to Home or History.
 */
class HistoryStats(
    val sessions: Int,
    val totalCounts: Int,
    val totalRounds: Int,
    val totalMillis: Long,
    /** Per local day, in date order. */
    val daily: Map<LocalDate, DayTotal>,
    val mantras: List<MantraTotal>
) {
    companion object {
        fun of(history: List<SessionRecord>, zone: ZoneId = ZoneId.systemDefault()): HistoryStats {
            val daily = PracticeStats.dailyTotals(history, zone)
            // The daily totals already add everything up
            var counts = 0
            var rounds = 0
            var millis = 0L
            for (total in daily.values) {
                counts += total.counts
                rounds += total.rounds
                millis += total.millis
            }
            return HistoryStats(history.size, counts, rounds, millis, daily, PracticeStats.mantraTotals(history))
        }
    }
}

/**
 * Insights derived from session history. Pure functions, so they're computed off the stored records
 * (the single source of truth) and unit tested.
 */
object PracticeStats {

    /** Totals per local day, in date order; a session belongs to the day it started. */
    fun dailyTotals(sessions: List<SessionRecord>, zone: ZoneId = ZoneId.systemDefault()): SortedMap<LocalDate, DayTotal> {
        val daily = TreeMap<LocalDate, DayTotal>()
        // Sessions are mostly newest first, so consecutive ones usually share a day: they are summed as a
        // run, converting the day once and touching the map once per run
        var dayStart = Long.MAX_VALUE
        var dayEnd = Long.MIN_VALUE
        var day = LocalDate.MIN
        var counts = 0
        var rounds = 0
        var millis = 0L
        var count = 0
        fun flush() {
            if (count == 0) return
            val total = daily[day]
            daily[day] = if (total == null) {
                DayTotal(counts, rounds, millis, count)
            } else {
                DayTotal(total.counts + counts, total.rounds + rounds, total.millis + millis, total.sessions + count)
            }
        }
        for (session in sessions) {
            if (session.startTime !in dayStart until dayEnd) {
                flush()
                counts = 0
                rounds = 0
                millis = 0L
                count = 0
                day = Instant.ofEpochMilli(session.startTime).atZone(zone).toLocalDate()
                dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
                dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }
            counts += session.totalCounts
            rounds += session.totalRounds
            millis += session.durationMillis
            count++
        }
        flush()
        return daily
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
        val days = if (daily is SortedMap) daily.keys else daily.keys.sorted()
        for (practiceDay in days.filter(::practiced)) {
            run = if (previous != null && previous.plusDays(1) == practiceDay) run + 1 else 1
            best = maxOf(best, run)
            previous = practiceDay
        }
        return Streak(current = current, best = maxOf(best, current), todayDone = todayDone)
    }

    /** Lifetime per-mantra totals, most chanted first. Names match ignoring case and surrounding spaces. */
    fun mantraTotals(sessions: List<SessionRecord>): List<MantraTotal> {
        // One pass, summing in place; the first spelling seen names the mantra
        class Sum(val name: String) {
            var rounds = 0
            var counts = 0
        }
        val sums = LinkedHashMap<String, Sum>()
        for (session in sessions) {
            for (tally in session.mantras) {
                if (tally.name.isBlank()) continue
                val name = tally.name.trim()
                val sum = sums.getOrPut(name.lowercase()) { Sum(name) }
                sum.rounds += tally.rounds
                sum.counts += tally.counts
            }
        }
        return sums.values.map { MantraTotal(it.name, it.rounds, it.counts) }
            .sortedWith(compareByDescending<MantraTotal> { it.counts }.thenBy { it.name.lowercase() })
    }

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
