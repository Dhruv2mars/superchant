package com.superchant.data

import java.util.concurrent.atomic.AtomicReference

/**
 * Saves values through an ordered queue ([enqueue]), coalescing values offered while a save is still
 * waiting in it: the waiting save writes the newest value when its turn comes. So every value is either
 * saved or replaced by a newer one before its turn, and saves stay in order with the rest of the queue.
 */
internal class LatestWins<T : Any>(
    private val enqueue: (suspend () -> Unit) -> Unit,
    private val save: suspend (T) -> Unit
) {
    private val pending = AtomicReference<T?>(null)

    fun offer(value: T) {
        if (pending.getAndSet(value) == null) enqueue { pending.getAndSet(null)?.let { save(it) } }
    }
}
