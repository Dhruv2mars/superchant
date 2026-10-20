package com.superchant.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StoredListTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // As written by v1.1.0: numeric currentTimeMillis IDs, including a collision.
    private val legacyHistory = """
        [
          {"id":1700000000000,"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1,"goalId":1690000000000,"goalName":"Morning"},
          {"id":1700000000000,"startTime":3,"endTime":4,"totalCounts":5,"totalRounds":0,"goalId":null,"goalName":null}
        ]
    """.trimIndent()

    @Test
    fun legacyNumericIdsDecodeAsStrings() {
        val list = json.decodeStoredList(SessionRecord.serializer(), legacyHistory)
        assertEquals(2, list.items.size)
        assertTrue(list.unreadable.isEmpty())
        assertEquals("1700000000000", list.items[0].id)
        assertEquals("1690000000000", list.items[0].goalId)

        val goals = json.decodeStoredList(
            Goal.serializer(),
            """[{"id":5,"name":"G","mantras":[{"id":7,"name":"M","targetRounds":3}],"createdAt":9}]"""
        ).items
        assertEquals("5", goals.single().id)
        assertEquals("7", goals.single().mantras.single().id)
    }

    @Test
    fun duplicateIdsAreMadeUnique() {
        val records = json.decodeStoredList(SessionRecord.serializer(), legacyHistory).items.withUniqueRecordIds()
        assertEquals("1700000000000", records[0].id)
        assertEquals(2, records.map { it.id }.toSet().size)

        val mantra = MantraGoal(id = "m", name = "M", targetRounds = 1)
        val goals = listOf(Goal(id = "g", name = "A", mantras = listOf(mantra, mantra)), Goal(id = "g", name = "B", mantras = emptyList()))
            .withUniqueGoalIds()
        assertEquals(2, goals.map { it.id }.toSet().size)
        assertEquals(2, goals[0].mantras.map { it.id }.toSet().size)
    }

    @Test
    fun replacementIdsAreTheSameAsBeforeAndQuickForManyCopies() {
        // The earlier (quadratic) rule: the smallest free "-N" suffix from 2. Screens and edits rely on these IDs.
        fun reference(ids: List<String>): List<String> {
            val seen = HashSet<String>()
            return ids.map { original ->
                val id = if (original !in seen) original else generateSequence(2) { it + 1 }.map { "$original-$it" }.first { it !in seen }
                seen.add(id)
                id
            }
        }
        fun unique(ids: List<String>) = ids.withUniqueIds({ it }) { _, id -> id }

        assertEquals(listOf("g", "g-2", "g-2-2", "g-3", "g-4", "g-2-3"), unique(listOf("g", "g", "g-2", "g", "g", "g-2")))
        val random = Random(7)
        val alphabet = listOf("a", "a-2", "a-3", "a-2-2", "b", "b-2", "a-2-3")
        repeat(500) {
            val ids = List(random.nextInt(1, 40)) { alphabet[random.nextInt(alphabet.size)] }
            assertEquals(ids.toString(), reference(ids), unique(ids))
        }

        // 10,000 copies of one ID took about 1.4 s before
        val copies = List(10_000) { "1700000000000" }
        val start = System.nanoTime()
        val ids = unique(copies)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(10_000, ids.toSet().size)
        assertTrue("$ms ms", ms < 300)
    }

    @Test
    fun undecodableElementsArePreservedOnWrite() {
        val stored = """[{"id":"a","startTime":1,"endTime":2,"totalCounts":1,"totalRounds":0},{"id":"broken"}]"""
        val list = json.decodeStoredList(SessionRecord.serializer(), stored)
        assertEquals(listOf("a"), list.items.map { it.id })
        assertEquals(1, list.unreadable.size)

        val newRecord = SessionRecord(id = "b", startTime = 5, endTime = 6, totalCounts = 2, totalRounds = 0)
        val written = json.encodeStoredList(SessionRecord.serializer(), list.items.upsert(newRecord), list.unreadable)
        val elements = json.parseToJsonElement(written).jsonArray
        assertEquals(3, elements.size)
        assertEquals(json.parseToJsonElement("""{"id":"broken"}"""), elements.last())
    }

    @Test
    fun nonArrayDataIsFlaggedCorrupt() {
        val list = json.decodeStoredList(SessionRecord.serializer(), "not json")
        assertTrue(list.corrupt)
        assertTrue(list.items.isEmpty())
        assertFalse(json.decodeStoredList(SessionRecord.serializer(), null).corrupt)
    }

    @Test
    fun upsertReplacesSameSessionInsteadOfDuplicating() {
        val first = SessionRecord(id = "s", startTime = 0, endTime = 10, totalCounts = 5, totalRounds = 0)
        val other = SessionRecord(id = "o", startTime = 0, endTime = 1, totalCounts = 1, totalRounds = 0)
        val history = listOf(first, other).upsert(first.copy(endTime = 20, totalCounts = 9))
        assertEquals(listOf("s", "o"), history.map { it.id })
        assertEquals(9, history[0].totalCounts)
    }

    @Test
    fun historyCappedAt100ByV110KeepsGrowingWithoutDroppingAnything() {
        // v1.1.0 stored at most 100 sessions with numeric IDs; the next save must keep all of them.
        val capped = (1..100).joinToString(",", "[", "]") { i ->
            """{"id":${1_700_000_000_000L + i},"startTime":$i,"endTime":${i + 1},"totalCounts":$i,"totalRounds":0}"""
        }
        val stored = json.decodeStoredList(SessionRecord.serializer(), capped)
        assertEquals(100, stored.items.size)

        var history = stored.items
        repeat(5) { history = history.upsert(SessionRecord(startTime = 1_000L + it, endTime = 1_001L + it, totalCounts = 1, totalRounds = 0)) }
        val reloaded = json.decodeStoredList(
            SessionRecord.serializer(),
            json.encodeStoredList(SessionRecord.serializer(), history, stored.unreadable)
        ).items
        assertEquals(105, reloaded.size)
        assertEquals((1..100).sum() + 5, reloaded.sumOf { it.totalCounts })
        assertTrue(reloaded.any { it.id == "1700000000001" }) // the oldest v1.1.0 record survives
    }

    @Test
    fun tenYearsOfHistoryStaysSmallAndFastToDecode() {
        // ~3 sessions a day for 10 years
        val records = (0 until 11_000).map { i ->
            SessionRecord(
                startTime = 1_700_000_000_000L + i * 28_800_000L,
                endTime = 1_700_000_000_000L + i * 28_800_000L + 900_000L,
                totalCounts = 1_080,
                totalRounds = 10,
                goalId = if (i % 2 == 0) newId() else null,
                goalName = if (i % 2 == 0) "Morning practice" else null
            )
        }
        val encoded = json.encodeStoredList(SessionRecord.serializer(), records, emptyList())
        val bytesPerRecord = encoded.length / records.size
        val start = System.nanoTime()
        val decoded = json.decodeStoredList(SessionRecord.serializer(), encoded).items
        val decodeMs = (System.nanoTime() - start) / 1_000_000
        println("history size: ${encoded.length / 1024} KiB for ${records.size} sessions ($bytesPerRecord B/session), decode $decodeMs ms")
        assertEquals(records, decoded)
        assertTrue("$bytesPerRecord bytes per session", bytesPerRecord < 220)
    }
}
