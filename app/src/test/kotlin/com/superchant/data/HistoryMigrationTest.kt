package com.superchant.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistoryMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val theme = intPreferencesKey("theme_mode")

    private fun record(id: String, start: Long) = SessionRecord(id = id, startTime = start, endTime = start + 1, totalCounts = 1, totalRounds = 0)
    private fun history(vararg records: SessionRecord) = json.encodeStoredList(SessionRecord.serializer(), records.toList(), emptyList())

    /** Opens both stores the way the app does (one "process"), runs [block], then closes them. */
    private fun launch(block: suspend (legacy: DataStore<Preferences>, history: DataStore<Preferences>) -> Unit) = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val legacy = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "superchant_prefs.preferences_pb") }
        val history = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "history.preferences_pb") }
        try {
            block(legacy, history)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun historyAndGoalsMoveAndSettingsStay() {
        val stored = history(record("a", 1))
        val goals = """[{"id":"g","name":"G","mantras":[]}]"""
        launch { legacy, _ ->
            legacy.edit {
                it[SESSION_HISTORY_KEY] = stored
                it[SAVED_GOALS_KEY] = goals
                it[theme] = 1
            }
        }
        launch { legacy, history ->
            moveHistory(legacy, history, json)
            val moved = history.data.first()
            assertEquals(stored, moved[SESSION_HISTORY_KEY])
            assertEquals(goals, moved[SAVED_GOALS_KEY])
            val old = legacy.data.first()
            assertNull(old[SESSION_HISTORY_KEY])
            assertNull(old[SAVED_GOALS_KEY])
            assertEquals(1, old[theme])
        }
        // Next start: nothing left to move, and nothing changes
        launch { legacy, history ->
            history.edit { it[SESSION_HISTORY_KEY] = history(record("b", 2), record("a", 1)) }
            moveHistory(legacy, history, json)
            assertEquals(history(record("b", 2), record("a", 1)), history.data.first()[SESSION_HISTORY_KEY])
        }
    }

    @Test
    fun anInterruptedMoveFinishesWithoutDuplicates() {
        val stored = history(record("a", 1))
        launch { legacy, history ->
            // Copied, but the app died before removing the old copy
            legacy.edit { it[SESSION_HISTORY_KEY] = stored }
            history.edit { it[SESSION_HISTORY_KEY] = stored }
            moveHistory(legacy, history, json)
            assertEquals(stored, history.data.first()[SESSION_HISTORY_KEY])
            assertNull(legacy.data.first()[SESSION_HISTORY_KEY])
        }
    }

    @Test
    fun listsInBothStoresAreMergedByIdNotLost() {
        launch { legacy, history ->
            // e.g. an older version was used for a while after the move
            history.edit { it[SESSION_HISTORY_KEY] = history(record("a", 1)) }
            legacy.edit { it[SESSION_HISTORY_KEY] = history(record("b", 5), record("a", 1)) }
            moveHistory(legacy, history, json)
            val merged = json.decodeStoredList(SessionRecord.serializer(), history.data.first()[SESSION_HISTORY_KEY]).items
            assertEquals(listOf("b", "a"), merged.map { it.id })
        }
    }

    @Test
    fun freshInstallHasNothingToMove() {
        launch { legacy, history ->
            moveHistory(legacy, history, json)
            assertNull(history.data.first()[SESSION_HISTORY_KEY])
            assertNull(legacy.data.first()[SAVED_GOALS_KEY])
        }
    }
}
