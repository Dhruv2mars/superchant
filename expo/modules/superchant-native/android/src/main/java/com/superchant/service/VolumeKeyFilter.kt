package com.superchant.service

import android.view.KeyEvent

/**
 * Decides which volume key events to consume. A new press (DOWN with repeatCount 0) is offered to
 * [onPress] with its downTime; if it is counted, the rest of that press (auto-repeats and the UP) is consumed too, so
 * no half of a counted press reaches the system volume control. Presses that are not counted
 * (no session, paused, goal complete) pass through untouched.
 */
class VolumeKeyFilter(private val onPress: (downTime: Long) -> Boolean) {

    private val consumedKeys = mutableSetOf<Int>()

    fun onKey(keyCode: Int, action: Int, repeatCount: Int, downTime: Long): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        return when (action) {
            KeyEvent.ACTION_DOWN ->
                if (repeatCount > 0) {
                    keyCode in consumedKeys
                } else {
                    onPress(downTime).also { counted -> if (counted) consumedKeys += keyCode else consumedKeys -= keyCode }
                }
            KeyEvent.ACTION_UP -> consumedKeys.remove(keyCode)
            else -> keyCode in consumedKeys
        }
    }
}
