package com.superchant.service

import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_VOLUME_DOWN
import android.view.KeyEvent.KEYCODE_VOLUME_UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyFilterTest {

    private var counting = true
    private var presses = 0
    private val filter = VolumeKeyFilter { if (counting) presses++; counting }

    @Test
    fun countedPressConsumesDownRepeatsAndUp() {
        assertTrue(filter.onKey(KEYCODE_VOLUME_UP, ACTION_DOWN, 0))
        assertTrue(filter.onKey(KEYCODE_VOLUME_UP, ACTION_DOWN, 1))
        assertTrue(filter.onKey(KEYCODE_VOLUME_UP, ACTION_DOWN, 2))
        assertTrue(filter.onKey(KEYCODE_VOLUME_UP, ACTION_UP, 0))
        // Holding the key counts once.
        assertEquals(1, presses)
    }

    @Test
    fun uncountedPressPassesThroughEntirely() {
        counting = false // no session, paused, or goal complete
        assertFalse(filter.onKey(KEYCODE_VOLUME_DOWN, ACTION_DOWN, 0))
        assertFalse(filter.onKey(KEYCODE_VOLUME_DOWN, ACTION_DOWN, 1))
        assertFalse(filter.onKey(KEYCODE_VOLUME_DOWN, ACTION_UP, 0))
        assertEquals(0, presses)
    }

    @Test
    fun upForAPressThatStartedBeforeTheSessionPassesThrough() {
        counting = false
        filter.onKey(KEYCODE_VOLUME_UP, ACTION_DOWN, 0)
        counting = true
        assertFalse(filter.onKey(KEYCODE_VOLUME_UP, ACTION_UP, 0))
        // The next press is counted again.
        assertTrue(filter.onKey(KEYCODE_VOLUME_UP, ACTION_DOWN, 0))
        assertTrue(filter.onKey(KEYCODE_VOLUME_UP, ACTION_UP, 0))
    }

    @Test
    fun upIsConsumedEvenIfCountingStoppedMidPress() {
        assertTrue(filter.onKey(KEYCODE_VOLUME_UP, ACTION_DOWN, 0))
        counting = false // e.g. this press completed the goal
        assertTrue(filter.onKey(KEYCODE_VOLUME_UP, ACTION_UP, 0))
        assertFalse(filter.onKey(KEYCODE_VOLUME_UP, ACTION_DOWN, 0))
    }

    @Test
    fun otherKeysAreIgnored() {
        assertFalse(filter.onKey(KEYCODE_ENTER, ACTION_DOWN, 0))
        assertEquals(0, presses)
    }
}
