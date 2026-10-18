package com.superchant.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray

/**
 * A JSON list read from storage. Elements that fail to decode are kept verbatim in [unreadable]
 * and written back unchanged, so a schema mismatch or one bad element never wipes the rest.
 * [corrupt] is true when the stored string was not a JSON array at all.
 */
class StoredList<T>(
    val items: List<T>,
    val unreadable: List<JsonElement> = emptyList(),
    val corrupt: Boolean = false
)

fun <T> Json.decodeStoredList(serializer: KSerializer<T>, stored: String?): StoredList<T> {
    if (stored == null) return StoredList(emptyList())
    val array = try {
        parseToJsonElement(stored).jsonArray
    } catch (e: Exception) {
        return StoredList(emptyList(), corrupt = true)
    }
    val items = mutableListOf<T>()
    val unreadable = mutableListOf<JsonElement>()
    for (element in array) {
        try {
            items += decodeFromJsonElement(serializer, element)
        } catch (e: Exception) {
            unreadable += element
        }
    }
    return StoredList(items, unreadable)
}

/** Encodes [items] followed by the preserved [unreadable] elements. */
fun <T> Json.encodeStoredList(serializer: KSerializer<T>, items: List<T>, unreadable: List<JsonElement>): String =
    encodeToString(JsonArray.serializer(), JsonArray(items.map { encodeToJsonElement(serializer, it) } + unreadable))

/**
 * Gives every item a unique ID. Data from v1.1.0 used currentTimeMillis IDs, which could collide
 * and crash LazyColumns keyed by ID; later duplicates get a fresh ID.
 */
fun <T> List<T>.withUniqueIds(id: (T) -> String, withId: (T, String) -> T): List<T> {
    val seen = HashSet<String>()
    return map { item -> if (seen.add(id(item))) item else withId(item, newId()).also { seen.add(id(it)) } }
}

fun List<Goal>.withUniqueGoalIds(): List<Goal> =
    withUniqueIds(Goal::id) { goal, id -> goal.copy(id = id) }
        .map { goal -> goal.copy(mantras = goal.mantras.withUniqueIds(MantraGoal::id) { m, id -> m.copy(id = id) }) }

fun List<SessionRecord>.withUniqueRecordIds(): List<SessionRecord> =
    withUniqueIds(SessionRecord::id) { record, id -> record.copy(id = id) }

/** Adds [record] at the top, replacing any record with the same ID, and keeps the newest [limit]. */
fun List<SessionRecord>.upsert(record: SessionRecord, limit: Int): List<SessionRecord> =
    (listOf(record) + filterNot { it.id == record.id }).take(limit)
