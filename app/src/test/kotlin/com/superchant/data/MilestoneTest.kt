package com.superchant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MilestoneTest {

    private fun feedbacks(counts: Int): List<CountFeedback> {
        var state = ChantState(isActive = true)
        return (1..counts).map { ChantCounter.increment(state)!!.also { state = it.state }.feedback }
    }

    private fun milestoneCounts(counts: Int) =
        feedbacks(counts).withIndex().filter { it.value == CountFeedback.MILESTONE }.map { it.index + 1 }

    @Test
    fun malaQuartersOn108AndRepeatEachRound() {
        assertEquals(listOf(27, 54, 81, 135, 162, 189), milestoneCounts(216))
        assertEquals(CountFeedback.ROUND, feedbacks(108).last())
    }
}
