package com.superchant.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BeadClickTest {

    @Test
    fun clickIsShortQuietAndDecays() {
        val samples = BeadClick.synthesize(durationMs = 45)
        assertEquals(44_100 * 45 / 1000, samples.size)
        assertEquals(0, samples[0].toInt())
        val peak = samples.maxOf { abs(it.toInt()) }
        assertTrue("audible", peak > 3_000)
        assertTrue("never clips", peak < 0.5 * Short.MAX_VALUE)
        val tail = samples.takeLast(200).maxOf { abs(it.toInt()) }
        assertTrue("fades out", tail < peak / 50)
    }
}
