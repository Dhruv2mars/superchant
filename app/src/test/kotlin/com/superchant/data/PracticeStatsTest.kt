package com.superchant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class PracticeStatsTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 10, 18)

    private fun session(day: LocalDate, rounds: Int, counts: Int = rounds * 108, hour: Int = 6, mantras: List<MantraTally> = emptyList()): SessionRecord {
        val start = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        return SessionRecord(startTime = start, endTime = start + 600_000, totalCounts = counts, totalRounds = rounds, mantras = mantras)
    }

    @Test
    fun dailyTotalsGroupByLocalStartDay() {
        val daily = PracticeStats.dailyTotals(
            listOf(session(today, 2), session(today, 1, hour = 23), session(today.minusDays(1), 4)),
            zone
        )
        assertEquals(DayTotal(counts = 324, rounds = 3, millis = 1_200_000, sessions = 2), daily[today])
        assertEquals(4, daily[today.minusDays(1)]?.rounds)
        // The same instant falls on the next day further east
        val east = PracticeStats.dailyTotals(listOf(session(today, 1, hour = 23)), ZoneOffset.ofHours(5))
        assertEquals(setOf(today.plusDays(1)), east.keys)
    }

    @Test
    fun streakStaysAliveUntilTodayIsDone() {
        val daily = PracticeStats.dailyTotals((1..3).map { session(today.minusDays(it.toLong()), 1) }, zone)
        val streak = PracticeStats.streak(daily, today, dailyTarget = 0)
        assertEquals(3, streak.current)
        assertFalse(streak.todayDone)

        val withToday = PracticeStats.dailyTotals((0..3).map { session(today.minusDays(it.toLong()), 1) }, zone)
        assertEquals(Streak(current = 4, best = 4, todayDone = true), PracticeStats.streak(withToday, today, 0))
    }

    @Test
    fun missedDayBreaksStreakButBestIsKept() {
        val days = listOf(0L, 1L, 4L, 5L, 6L, 7L, 8L).map { session(today.minusDays(it), 1) }
        val streak = PracticeStats.streak(PracticeStats.dailyTotals(days, zone), today, 0)
        assertEquals(2, streak.current)
        assertEquals(5, streak.best)
        assertEquals(0, PracticeStats.streak(emptyMap(), today, 0).current)
    }

    @Test
    fun dailyTargetDecidesWhatCountsAsPractice() {
        val daily = PracticeStats.dailyTotals(listOf(session(today, 2), session(today.minusDays(1), 16)), zone)
        assertEquals(2, PracticeStats.streak(daily, today, dailyTarget = 0).current)
        val withTarget = PracticeStats.streak(daily, today, dailyTarget = 16)
        assertEquals(1, withTarget.current)
        assertFalse(withTarget.todayDone)
        assertTrue(PracticeStats.isPracticeDay(DayTotal(counts = 5), 0))
        assertFalse(PracticeStats.isPracticeDay(DayTotal(counts = 5), 1))
    }

    @Test
    fun mantraTotalsMergeByNameAndSortByCounts() {
        val sessions = listOf(
            session(today, 3, mantras = listOf(MantraTally("Om Namah Shivaya", 2, 216), MantraTally("Gayatri", 1, 120))),
            session(today, 1, mantras = listOf(MantraTally(" om namah shivaya ", 1, 108))),
            session(today, 1)
        )
        assertEquals(
            listOf(MantraTotal("Om Namah Shivaya", 3, 324), MantraTotal("Gayatri", 1, 120)),
            PracticeStats.mantraTotals(sessions)
        )
    }

    @Test
    fun heatLevelsStepUpToTheReference() {
        assertEquals(0, PracticeStats.heatLevel(null, 16))
        assertEquals(0, PracticeStats.heatLevel(DayTotal(), 16))
        assertEquals(1, PracticeStats.heatLevel(DayTotal(counts = 10, rounds = 0), 16))
        assertEquals(2, PracticeStats.heatLevel(DayTotal(counts = 1, rounds = 6), 16))
        assertEquals(4, PracticeStats.heatLevel(DayTotal(counts = 1, rounds = 16), 16))
        assertEquals(4, PracticeStats.heatLevel(DayTotal(counts = 1, rounds = 40), 16))
    }

    @Test
    fun recordsCarryAPerMantraBreakdownThatAddsUp() {
        val goal = Goal(name = "G", mantras = listOf(MantraGoal(name = "A", targetRounds = 1), MantraGoal(name = "B", targetRounds = 2)))
        var state = ChantState.start(goal, now = 0, roundSize = 10)
        repeat(25) { state = ChantCounter.increment(state)!!.state }
        val record = state.toRecord(endTime = 1)
        assertEquals(listOf(MantraTally("A", 1, 10), MantraTally("B", 1, 15)), record.mantras)
        assertEquals(record.totalCounts, record.mantras.sumOf { it.counts })
        assertTrue(ChantState.start(null, 0).copy(count = 5).toRecord(1).mantras.isEmpty())
    }
}
