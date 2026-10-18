package com.superchant.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundSizeTest {

    private fun ChantState.incTimes(n: Int): ChantState = (1..n).fold(this) { s, _ -> ChantCounter.increment(s)!!.state }

    @Test
    fun roundsRollAtTheSessionRoundSize() {
        val small = ChantState(isActive = true, roundSize = 27)
        val at26 = small.incTimes(26)
        assertEquals(0, at26.rounds)
        val round = ChantCounter.increment(at26)!!
        assertEquals(CountFeedback.ROUND, round.feedback)
        assertEquals(1, round.state.rounds)
        assertEquals(0, round.state.currentRoundCount)
        assertEquals(0.5f, ChantState(count = 27, roundSize = 54).progress)
    }

    @Test
    fun undoStepsBackAcrossACustomRoundBoundary() {
        val atRound = ChantState(isActive = true, roundSize = 10).incTimes(10)
        val undone = ChantCounter.undo(atRound)!!
        assertEquals(9, undone.count)
        assertEquals(0, undone.rounds)
    }

    @Test
    fun goalAdvancesMantrasAtItsRoundSize() {
        val goal = Goal(name = "G", mantras = listOf(MantraGoal(id = "1", name = "A", targetRounds = 1), MantraGoal(id = "2", name = "B", targetRounds = 1)), roundSize = 27)
        val state = ChantState(isActive = true, activeGoal = goal, roundSize = RoundSize.forSession(goal, appDefault = 108))
        val afterFirst = state.incTimes(27)
        assertEquals(1, afterFirst.currentMantraIndex)
        val done = afterFirst.incTimes(26)
        assertFalse(done.isGoalComplete)
        val result = ChantCounter.increment(done)!!
        assertEquals(CountFeedback.GOAL, result.feedback)
        assertNull(ChantCounter.increment(result.state))
    }

    @Test
    fun sessionSizePrefersGoalThenSettingAndSanitizes() {
        val plain = Goal(name = "G", mantras = emptyList())
        assertEquals(54, RoundSize.forSession(null, 54))
        assertEquals(54, RoundSize.forSession(plain, 54))
        assertEquals(27, RoundSize.forSession(plain.copy(roundSize = 27), 54))
        assertEquals(54, RoundSize.forSession(plain.copy(roundSize = 0), 54))
        assertEquals(RoundSize.DEFAULT, RoundSize.sanitize(null))
        assertEquals(RoundSize.DEFAULT, RoundSize.sanitize(-5))
        assertEquals(RoundSize.DEFAULT, RoundSize.sanitize(RoundSize.MAX + 1))
        assertTrue(RoundSize.isValid(1) && RoundSize.isValid(RoundSize.MAX))
    }

    @Test
    fun oldJsonWithoutRoundSizeStillDecodes() {
        val json = Json { ignoreUnknownKeys = true }
        val goal = json.decodeFromString<Goal>("""{"id":1,"name":"Old","mantras":[{"id":2,"name":"A","targetRounds":3}],"createdAt":5}""")
        assertNull(goal.roundSize)
        val record = json.decodeFromString<SessionRecord>("""{"id":1,"startTime":0,"endTime":10,"totalCounts":216,"totalRounds":2}""")
        assertEquals(RoundSize.DEFAULT, record.roundSize)
    }
}
