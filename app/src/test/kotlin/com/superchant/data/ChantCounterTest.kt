package com.superchant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChantCounterTest {

    private val active = ChantState(isActive = true)
    private val goal = Goal(
        name = "Morning",
        mantras = listOf(MantraGoal(id = "1", name = "A", targetRounds = 1), MantraGoal(id = "2", name = "B", targetRounds = 2))
    )

    private fun ChantState.inc() = ChantCounter.increment(this)!!
    private fun ChantState.incTimes(n: Int): ChantState = (1..n).fold(this) { s, _ -> s.inc().state }

    @Test
    fun incrementCountsAndRollsOverAt108() {
        val first = active.inc()
        assertEquals(1, first.state.count)
        assertEquals(CountFeedback.COUNT, first.feedback)

        val at107 = active.incTimes(107)
        val round = at107.inc()
        assertEquals(108, round.state.count)
        assertEquals(1, round.state.rounds)
        assertEquals(0, round.state.currentRoundCount)
        assertEquals(CountFeedback.ROUND, round.feedback)
    }

    @Test
    fun incrementIgnoredWhenInactivePausedOrGoalComplete() {
        assertNull(ChantCounter.increment(ChantState()))
        assertNull(ChantCounter.increment(active.copy(isPaused = true)))
        assertNull(ChantCounter.increment(active.copy(activeGoal = goal, currentMantraIndex = 1, mantraRoundsCompleted = 2)))
    }

    @Test
    fun goalAdvancesThroughMantrasAndCompletes() {
        val start = active.copy(activeGoal = goal)

        val section = start.incTimes(107).inc()
        assertEquals(CountFeedback.SECTION, section.feedback)
        assertEquals(1, section.state.currentMantraIndex)
        assertEquals(0, section.state.mantraRoundsCompleted)

        val midB = section.state.incTimes(108)
        assertEquals(1, midB.mantraRoundsCompleted)
        assertFalse(midB.isGoalComplete)

        val done = midB.incTimes(107).inc()
        assertEquals(CountFeedback.GOAL, done.feedback)
        assertTrue(done.state.isGoalComplete)
        assertEquals(324, done.state.count)
        assertNull(ChantCounter.increment(done.state))
    }

    @Test
    fun debouncerDropsFastRepeats() {
        val debouncer = CountDebouncer(minIntervalMs = 150)
        assertTrue(debouncer.tryAccept(1_000))
        assertFalse(debouncer.tryAccept(1_040))
        assertFalse(debouncer.tryAccept(1_149))
        assertTrue(debouncer.tryAccept(1_150))
        assertTrue(debouncer.tryAccept(2_000))
    }
}
