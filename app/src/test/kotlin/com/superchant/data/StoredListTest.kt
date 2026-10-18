package com.superchant.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun undecodableElementsArePreservedOnWrite() {
        val stored = """[{"id":"a","startTime":1,"endTime":2,"totalCounts":1,"totalRounds":0},{"id":"broken"}]"""
        val list = json.decodeStoredList(SessionRecord.serializer(), stored)
        assertEquals(listOf("a"), list.items.map { it.id })
        assertEquals(1, list.unreadable.size)

        val newRecord = SessionRecord(id = "b", startTime = 5, endTime = 6, totalCounts = 2, totalRounds = 0)
        val written = json.encodeStoredList(SessionRecord.serializer(), list.items.upsert(newRecord, 100), list.unreadable)
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
        val history = listOf(first, other).upsert(first.copy(endTime = 20, totalCounts = 9), limit = 100)
        assertEquals(listOf("s", "o"), history.map { it.id })
        assertEquals(9, history[0].totalCounts)
        assertEquals(1, listOf(first, other).upsert(first, limit = 1).size)
    }
}
