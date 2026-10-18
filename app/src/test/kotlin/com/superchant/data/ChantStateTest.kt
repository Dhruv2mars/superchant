package com.superchant.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChantStateTest {

    private val goal = Goal(
        id = "g",
        name = "Morning",
        mantras = listOf(MantraGoal(id = "a", name = "A", targetRounds = 1), MantraGoal(id = "b", name = "B", targetRounds = 2))
    )

    /** Presses [times] times, returning the final state and every feedback event. */
    private fun ChantState.press(times: Int): Pair<ChantState, List<CountFeedback>> {
        var state = this
        val feedback = mutableListOf<CountFeedback>()
        repeat(times) {
            val result = ChantCounter.increment(state) ?: return state to feedback
            state = result.state.copy(lastUpdatedAt = 1_000L + it)
            feedback += result.feedback
        }
        return state to feedback
    }

    @Test
    fun roundCountRollsOverAt108() {
        assertEquals(107, ChantState(count = 107).currentRoundCount)
        assertEquals(0, ChantState(count = 108).currentRoundCount)
        assertEquals(1, ChantState(count = 109).currentRoundCount)
        assertEquals(0f, ChantState(count = 216).progress)
        assertEquals(0.5f, ChantState(count = 54).progress)
    }

    @Test
    fun goalCompletesOnlyAfterLastMantraTarget() {
        assertFalse(ChantState(activeGoal = goal, currentMantraIndex = 0, mantraRoundsCompleted = 1).isGoalComplete)
        assertFalse(ChantState(activeGoal = goal, currentMantraIndex = 1, mantraRoundsCompleted = 1).isGoalComplete)
        assertTrue(ChantState(activeGoal = goal, currentMantraIndex = 1, mantraRoundsCompleted = 2).isGoalComplete)
        assertEquals("B", ChantState(activeGoal = goal, currentMantraIndex = 1).currentMantra?.name)
        assertFalse(ChantState().isGoalComplete)
    }

    @Test
    fun recordKeepsSessionIdAndGoal() {
        val (state, _) = ChantState.start(goal, now = 100).press(5)
        val record = state.toRecord(endTime = 900)
        assertEquals(state.sessionId, record.id)
        assertEquals("g", record.goalId)
        assertEquals("Morning", record.goalName)
        assertEquals(5, record.totalCounts)
        assertEquals(800L, record.durationMillis)
        // A clock that moved backwards never yields a negative duration.
        assertEquals(0L, state.toRecord(endTime = 50).durationMillis)
    }

    @Test
    fun startCreatesUniqueSessionIds() {
        assertNotEquals(ChantState.start(null, 0).sessionId, ChantState.start(null, 0).sessionId)
    }

    @Test
    fun restoreResumesRecentSessionAndFinishesStaleOne() {
        val (state, _) = ChantState.start(goal, now = 0).press(10)
        val lastUpdate = state.lastUpdatedAt

        assertEquals(RestoreAction.Resume(state), state.restoreAction(now = lastUpdate + 60_000))

        val stale = state.restoreAction(now = lastUpdate + ChantState.STALE_AFTER_MS + 1)
        assertEquals(RestoreAction.Finish(state.toRecord(lastUpdate)), stale)

        assertEquals(RestoreAction.Discard, ChantState.start(null, 0).restoreAction(now = ChantState.STALE_AFTER_MS + 1))
        assertEquals(RestoreAction.Discard, ChantState().restoreAction(now = 0))
    }

    @Test
    fun activeSessionSurvivesJsonRoundTrip() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val (state, _) = ChantState.start(goal, now = 0).press(150)
        val restored = json.decodeFromString(ChantState.serializer(), json.encodeToString(ChantState.serializer(), state))
        assertEquals(state, restored)
        assertEquals(1, restored.currentMantraIndex)
    }
}
