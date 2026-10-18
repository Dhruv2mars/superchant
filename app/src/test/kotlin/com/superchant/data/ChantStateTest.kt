package com.superchant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChantStateTest {

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
        val goal = Goal(
            name = "Morning",
            mantras = listOf(MantraGoal(id = 1, name = "A", targetRounds = 1), MantraGoal(id = 2, name = "B", targetRounds = 2))
        )
        assertFalse(ChantState(activeGoal = goal, currentMantraIndex = 0, mantraRoundsCompleted = 1).isGoalComplete)
        assertFalse(ChantState(activeGoal = goal, currentMantraIndex = 1, mantraRoundsCompleted = 1).isGoalComplete)
        assertTrue(ChantState(activeGoal = goal, currentMantraIndex = 1, mantraRoundsCompleted = 2).isGoalComplete)
        assertEquals("B", ChantState(activeGoal = goal, currentMantraIndex = 1).currentMantra?.name)
        assertFalse(ChantState().isGoalComplete)
    }
}
