package com.superchant.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumePressTrackerTest {

    private val tracker = VolumePressTracker(staleMs = 2_000)

    @Test
    fun eachPressCountsOnce() {
        assertTrue(tracker.onAdjust(1, 0))
        assertFalse(tracker.onAdjust(0, 15)) // release
        assertTrue(tracker.onAdjust(-1, 300)) // either button counts
        assertFalse(tracker.onAdjust(0, 310))
    }

    @Test
    fun holdingAButtonCountsOnce() {
        // Down, then auto-repeats after the repeat timeout and every 50 ms, then the release
        val counted = listOf(0L, 500L, 550L, 600L, 650L).count { tracker.onAdjust(1, it) }
        tracker.onAdjust(0, 660)
        assertEquals(1, counted)
        assertTrue(tracker.onAdjust(1, 900))
    }

    @Test
    fun aPressAfterAMissedReleaseStillCounts() {
        assertTrue(tracker.onAdjust(1, 0))
        // The release went elsewhere; a later press counts once the stale window has passed
        assertFalse(tracker.onAdjust(1, 1_500))
        assertTrue(tracker.onAdjust(1, 3_600))
    }

    @Test
    fun aReleaseAloneCountsNothing() {
        assertFalse(tracker.onAdjust(0, 0))
        assertTrue(tracker.onAdjust(1, 10))
    }
}
