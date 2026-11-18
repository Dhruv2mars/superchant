package com.superchant.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Counts volume presses outside SuperChant's own screen (other apps, the lock screen) without an
 * accessibility service. An enabled accessibility service that filters keys makes Play Integrity report
 * the device as "controlled" by another app, and Google Pay refuses payments while that is so.
 *
 * This is a media session with a remote volume provider. While it reports PLAYING, Android sends it
 * the volume keys that the app on screen doesn't handle itself, and each one arrives in
 * [VolumeProvider.onAdjustVolume]. While it is PAUSED, the keys change the volume as usual.
 *
 * Android gives the keys to the session that most recently started playing, so when another app starts
 * playing media (a bhajan in a music app), the session restarts its own PLAYING state to take them back,
 * as the accessibility service did. A foreground app that handles volume keys itself (a video player, the
 * camera) still gets them first.
 *
 * Call on the main thread; [onPress] runs there too.
 */
class VolumeKeySession(context: Context, private val onPress: () -> Unit) {

    private val presses = VolumePressTracker()
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val session = MediaSession(context, TAG).apply {
        // Without a callback the framework drops volume adjustments before they reach the provider.
        setCallback(object : MediaSession.Callback() {}, handler)
        // The volume never changes, so the system volume slider stays still while presses count.
        setPlaybackToRemote(object : VolumeProvider(VOLUME_CONTROL_RELATIVE, MAX_VOLUME, MAX_VOLUME / 2) {
            override fun onAdjustVolume(direction: Int) {
                if (presses.onAdjust(direction, SystemClock.uptimeMillis())) onPress()
            }
        })
        isActive = true
    }

    private var counting: Boolean? = null
    private var otherMediaPlaying = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            // The active players; SuperChant's own bead click is a sonification sound, not media.
            val mediaPlaying = configs.any {
                val usage = it.audioAttributes.usage
                usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME
            }
            if (mediaPlaying && !otherMediaPlaying && counting == true) {
                // After the player's own session has gone PLAYING, which may come just after its audio starts
                handler.removeCallbacks(reclaimKeys)
                handler.postDelayed(reclaimKeys, RECLAIM_DELAY_MS)
            }
            otherMediaPlaying = mediaPlaying
        }
    }

    private val reclaimKeys = Runnable {
        if (counting == true) {
            setState(PlaybackState.STATE_PAUSED)
            setState(PlaybackState.STATE_PLAYING)
        }
    }

    init {
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
    }

    /** Takes the volume keys while [on]; otherwise leaves them to the system volume. */
    fun setCounting(on: Boolean) {
        if (counting == on) return
        counting = on
        if (!on) handler.removeCallbacks(reclaimKeys)
        setState(if (on) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
    }

    private fun setState(state: Int) {
        session.setPlaybackState(
            PlaybackState.Builder().setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f).build()
        )
    }

    fun release() {
        handler.removeCallbacks(reclaimKeys)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        session.release()
    }

    private companion object {
        const val TAG = "SuperChant"
        const val MAX_VOLUME = 100
        const val RECLAIM_DELAY_MS = 1_000L
    }
}

/**
 * Turns a remote volume provider's adjustments into presses. Android sends one non-zero adjustment for
 * each key down, including auto-repeats while the key is held, and a zero adjustment when it is
 * released. Only the first adjustment of a press counts. If a release never arrives (it went to another
 * window), a press after [staleMs] without adjustments counts anyway; auto-repeats come far more often.
 */
class VolumePressTracker(private val staleMs: Long = 2_000) {

    private var held = false
    private var lastAdjustAt = 0L

    /** Returns true if this adjustment is a new press. */
    fun onAdjust(direction: Int, now: Long): Boolean {
        if (direction == 0) {
            held = false
            return false
        }
        val newPress = !held || now - lastAdjustAt > staleMs
        held = true
        lastAdjustAt = now
        return newPress
    }
}
