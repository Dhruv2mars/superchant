package com.superchant.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestWinsTest {

    private val queue = ArrayDeque<suspend () -> Unit>()
    private val log = mutableListOf<String>()
    private val latest = LatestWins<Int>({ queue.addLast(it) }) { log += "save $it" }

    private fun drain() = runBlocking { while (queue.isNotEmpty()) queue.removeFirst()() }

    @Test
    fun valuesOfferedWhileASaveWaitsCollapseIntoTheNewest() {
        (1..5).forEach(latest::offer)
        assertEquals(1, queue.size)
        drain()
        assertEquals(listOf("save 5"), log)
    }

    @Test
    fun aValueOfferedAfterTheSaveRanIsSavedAgain() {
        latest.offer(1)
        drain()
        latest.offer(2)
        drain()
        assertEquals(listOf("save 1", "save 2"), log)
    }

    @Test
    fun savesKeepTheirPlaceAmongOtherQueuedWork() {
        latest.offer(1)
        queue.addLast { log += "finish" }
        latest.offer(2) // still waiting ahead of "finish": it saves 2 there
        drain()
        assertEquals(listOf("save 2", "finish"), log)
        latest.offer(3)
        queue.addLast { log += "other" }
        drain()
        assertEquals(listOf("save 2", "finish", "save 3", "other"), log)
    }
}
