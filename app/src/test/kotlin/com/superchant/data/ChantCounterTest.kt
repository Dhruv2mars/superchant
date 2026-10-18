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
        mantras = listOf(MantraGoal(id = 1, name = "A", targetRounds = 1), MantraGoal(id = 2, name = "B", targetRounds = 2))
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
    fun undoAtZeroIsNoOp() {
        assertNull(ChantCounter.undo(active))
        assertNull(ChantCounter.undo(ChantState(count = 5)))
        assertNull(ChantCounter.undo(active.copy(count = 5, isPaused = true)))
    }

    @Test
    fun undoAcrossRoundBoundary() {
        val at108 = active.incTimes(108)
        val undone = ChantCounter.undo(at108)!!
        assertEquals(107, undone.count)
        assertEquals(0, undone.rounds)
        assertEquals(107, undone.currentRoundCount)
        // Counting again re-completes the round.
        assertEquals(CountFeedback.ROUND, undone.inc().feedback)
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
    fun undoReopensCompletedGoal() {
        val done = active.copy(activeGoal = goal).incTimes(324)
        assertTrue(done.isGoalComplete)
        val undone = ChantCounter.undo(done)!!
        assertFalse(undone.isGoalComplete)
        assertEquals(1, undone.currentMantraIndex)
        assertEquals(1, undone.mantraRoundsCompleted)
        assertEquals(CountFeedback.GOAL, undone.inc().feedback)
    }

    @Test
    fun undoStepsBackIntoPreviousMantra() {
        val advanced = active.copy(activeGoal = goal).incTimes(108)
        assertEquals(1, advanced.currentMantraIndex)
        val undone = ChantCounter.undo(advanced)!!
        assertEquals(0, undone.currentMantraIndex)
        assertEquals(0, undone.mantraRoundsCompleted)
        assertEquals(107, undone.count)
        assertEquals(CountFeedback.SECTION, undone.inc().feedback)
    }

    @Test
    fun undoInsideRoundLeavesGoalProgressAlone() {
        val state = active.copy(activeGoal = goal).incTimes(110)
        val undone = ChantCounter.undo(state)!!
        assertEquals(109, undone.count)
        assertEquals(1, undone.currentMantraIndex)
        assertEquals(0, undone.mantraRoundsCompleted)
    }

    @Test
    fun undoIsExactInverseOfIncrementOverWholeGoal() {
        var state = active.copy(activeGoal = goal)
        val history = mutableListOf(state)
        while (true) {
            state = ChantCounter.increment(state)?.state ?: break
            history += state
        }
        for (i in history.size - 1 downTo 1) {
            assertEquals(history[i - 1], ChantCounter.undo(history[i]))
        }
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
