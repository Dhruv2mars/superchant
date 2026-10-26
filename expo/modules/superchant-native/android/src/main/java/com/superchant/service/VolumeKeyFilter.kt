package com.superchant.service

import android.view.KeyEvent

/**
 * Decides which volume key events to consume. A new press (DOWN with repeatCount 0) is offered to
 * [onPress]; if it is counted, the rest of that press (auto-repeats and the UP) is consumed too, so
 * no half of a counted press reaches the system volume control. Presses that are not counted
 * (no session, paused, goal complete) pass through untouched.
 */
class VolumeKeyFilter(private val onPress: () -> Boolean) {

    private val consumedKeys = mutableSetOf<Int>()

    fun onKey(keyCode: Int, action: Int, repeatCount: Int): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        return when (action) {
            KeyEvent.ACTION_DOWN ->
                if (repeatCount > 0) {
                    keyCode in consumedKeys
                } else {
                    onPress().also { counted -> if (counted) consumedKeys += keyCode else consumedKeys -= keyCode }
                }
            KeyEvent.ACTION_UP -> consumedKeys.remove(keyCode)
            else -> keyCode in consumedKeys
        }
    }
}
