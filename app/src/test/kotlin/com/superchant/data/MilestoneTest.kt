package com.superchant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MilestoneTest {

    private fun feedbacks(roundSize: Int, counts: Int): List<CountFeedback> {
        var state = ChantState(isActive = true, roundSize = roundSize)
        return (1..counts).map { ChantCounter.increment(state)!!.also { state = it.state }.feedback }
    }

    private fun milestoneCounts(roundSize: Int, counts: Int) =
        feedbacks(roundSize, counts).withIndex().filter { it.value == CountFeedback.MILESTONE }.map { it.index + 1 }

    @Test
    fun malaQuartersOn108AndRepeatEachRound() {
        assertEquals(listOf(27, 54, 81, 135, 162, 189), milestoneCounts(108, 216))
        assertEquals(CountFeedback.ROUND, feedbacks(108, 108).last())
    }

    @Test
    fun intervalFollowsTheRoundSize() {
        assertEquals(27, RoundSize.milestoneInterval(54))
        assertEquals(listOf(27), milestoneCounts(54, 54))
        assertEquals(0, RoundSize.milestoneInterval(27))
        assertEquals(emptyList<Int>(), milestoneCounts(27, 54))
        assertEquals(8, RoundSize.milestoneInterval(32))
        assertEquals(0, RoundSize.milestoneInterval(33))
        assertEquals(0, RoundSize.milestoneInterval(12))
    }
}
