package com.superchant.bridge

import android.content.Context
import android.view.KeyEvent
import com.superchant.service.ChantingService
import com.superchant.service.VolumeKeyFilter
import expo.modules.core.interfaces.Package
import expo.modules.core.interfaces.ReactActivityHandler

/** Autolinked by Expo; hooks SuperChant's activity. */
class SuperchantPackage : Package {
    override fun createReactActivityHandlers(activityContext: Context): List<ReactActivityHandler> =
        listOf(ForegroundVolumeKeys())
}

/**
 * Counts volume presses while SuperChant's own window has focus, without the accessibility service
 * (payment apps such as Google Pay refuse to run while one is enabled). Ordinary key handling: the
 * activity gets a key only when the accessibility service didn't count it, so a press never counts twice.
 */
private class ForegroundVolumeKeys : ReactActivityHandler {
    private val filter = VolumeKeyFilter(ChantingService::onForegroundVolumeKeyPressed)

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        event != null && filter.onKey(keyCode, KeyEvent.ACTION_DOWN, event.repeatCount, event.downTime)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        filter.onKey(keyCode, KeyEvent.ACTION_UP, 0, event?.downTime ?: 0L)
}
