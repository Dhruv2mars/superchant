package com.superchant

import android.os.Build
import android.os.Trace

/**
 * Trace sections for profiling with Perfetto/systrace (near free when tracing is off). Sections are
 * prefixed "SC." so traces and macrobenchmarks can find them.
 */
object PerfTrace {
    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    /** An async slice, for work that suspends or hops threads. */
    fun beginAsync(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection(name, cookie)
    }

    fun endAsync(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.endAsyncSection(name, cookie)
    }

    private const val COUNT_TO_DRAW = "SC.countToDraw"
    private var lastQueued = 0
    private var lastDrawn = 0

    /** A count was taken; an async slice runs until the counter draws it. Main thread only. */
    fun countTaken(count: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (count <= lastQueued) countDrawn(Int.MAX_VALUE)
        if (count <= lastDrawn) lastDrawn = count - 1
        lastQueued = count
        Trace.beginAsyncSection(COUNT_TO_DRAW, count)
    }

    /** The counter drew [count]; ends the slices of every count up to it. Main thread only. */
    fun countDrawn(count: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val upTo = minOf(count, lastQueued)
        while (lastDrawn < upTo) Trace.endAsyncSection(COUNT_TO_DRAW, ++lastDrawn)
    }
}
