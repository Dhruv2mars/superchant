package com.superchant.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * A soft wooden "tock", synthesized once into a static AudioTrack so no audio asset is needed and
 * each play starts instantly. It uses the sonification stream, so it follows the system sounds volume
 * and stays quiet when the phone is on silent or vibrate.
 */
class BeadClick {

    private val track: AudioTrack? = try {
        val samples = synthesize()
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .also { it.write(samples, 0, samples.size) }
    } catch (e: Exception) {
        Log.w(TAG, "Bead click unavailable", e)
        null
    }

    fun play() {
        val track = track ?: return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Bead click failed", e)
        }
    }

    fun release() {
        track?.release()
    }

    companion object {
        private const val TAG = "BeadClick"
        private const val SAMPLE_RATE = 44_100

        /** Two damped partials (a hollow wood body) plus a short noise transient (the bead strike). */
        internal fun synthesize(durationMs: Int = 45): ShortArray {
            val n = SAMPLE_RATE * durationMs / 1000
            val random = Random(108)
            return ShortArray(n) { i ->
                val t = i.toDouble() / SAMPLE_RATE
                val body = 0.65 * sin(2 * PI * 1250 * t) * exp(-t / 0.008) +
                    0.35 * sin(2 * PI * 2900 * t) * exp(-t / 0.004)
                val strike = if (t < 0.0015) (random.nextDouble() * 2 - 1) * 0.5 * (1 - t / 0.0015) else 0.0
                // Raised-cosine fade-in avoids a click at sample 0
                val attack = if (t < 0.0004) 0.5 - 0.5 * kotlin.math.cos(PI * t / 0.0004) else 1.0
                ((body + strike) * attack * 0.32 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
    }
}
