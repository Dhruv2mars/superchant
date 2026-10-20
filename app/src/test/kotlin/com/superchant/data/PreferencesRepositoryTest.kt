package com.superchant.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class PreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A real store whose reads or writes can be made to fail, like a broken or full disk. */
    private class FlakyStore(private val store: DataStore<Preferences>) : DataStore<Preferences> {
        @Volatile var failReads = false
        @Volatile var failWrites = false

        override val data: Flow<Preferences> = flow {
            if (failReads) throw IOException("unreadable")
            emitAll(store.data)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failWrites) throw IOException("disk full")
            return store.updateData(transform)
        }
    }

    private fun store(name: String) = FlakyStore(PreferenceDataStoreFactory.create { tmp.root.resolve("$name.preferences_pb") })

    private val prefs by lazy { store("prefs") }
    private val repository by lazy { PreferencesRepository(prefs, store("session")) }

    private val json = Json { encodeDefaults = true }
    private val goalsKey = stringPreferencesKey("saved_goals")
    private val historyKey = stringPreferencesKey("session_history")
    private val goal = Goal(id = "g", name = "Morning", mantras = listOf(MantraGoal(id = "m", name = "Om", targetRounds = 1)), createdAt = 1)
    private val record = SessionRecord(id = "s", startTime = 1, endTime = 2, totalCounts = 108, totalRounds = 1)

    private fun goalsJson(vararg goals: Goal) = json.encodeToString(ListSerializer(Goal.serializer()), goals.toList())
    private fun historyJson(vararg records: SessionRecord) = json.encodeToString(ListSerializer(SessionRecord.serializer()), records.toList())
    private suspend fun storedGoals() = json.decodeStoredList(Goal.serializer(), prefs.data.first()[goalsKey]).items

    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(20_000) { block() } }
    private suspend fun <T> StateFlow<T?>.await(predicate: (T) -> Boolean): T = filterNotNull().first(predicate)

    @Test
    fun editsAndDeletesHitTheDuplicateThatWasShown() = test {
        // v1.1.0 data: two goals with the same ID, shown as "g" and "g-2"
        prefs.edit { it[goalsKey] = goalsJson(goal, goal.copy(name = "Evening")) }
        assertEquals(listOf("g", "g-2"), repository.goals.await { it.size == 2 }.map { it.id })

        repository.saveGoal(goal.copy(id = "g-2", name = "Night"))
        assertEquals(listOf("Morning", "Night"), storedGoals().map { it.name })

        repository.deleteGoal("g-2")
        assertEquals(listOf("Morning"), storedGoals().map { it.name })
    }

    @Test
    fun importingAnExportIntoDataWithDuplicateIdsAddsNothing() = test {
        prefs.edit {
            it[goalsKey] = goalsJson(goal, goal.copy(name = "Evening"))
            it[historyKey] = historyJson(record, record.copy(startTime = 5))
        }
        val backup = BackupCodec.decode(BackupCodec.encode(repository.exportBackup(now = 0)))

        assertEquals(ImportResult(sessionsAdded = 0, goalsAdded = 0, goalsUpdated = 2), repository.importBackup(backup))
        assertEquals(listOf("g", "g-2"), storedGoals().map { it.id })
        assertEquals(2, json.decodeStoredList(SessionRecord.serializer(), prefs.data.first()[historyKey]).items.size)
    }

    @Test
    fun unreadableStoredListsAreKeptAsABackupBeforeBeingReplaced() = test {
        prefs.edit {
            it[goalsKey] = "{not json"
            it[historyKey] = "also not json"
        }
        repository.importBackup(Backup(goals = listOf(goal), history = listOf(record)))
        repository.saveGoal(goal.copy(id = "g2"))

        val backups = prefs.data.first().asMap().filterKeys { "_backup_" in it.name }
        assertEquals(setOf("{not json", "also not json"), backups.values.toSet())
        assertEquals(listOf("g2", "g"), storedGoals().map { it.id })
    }

    @Test
    fun aFailedReadIsRetriedInsteadOfShowingDefaultsForever() = test {
        prefs.edit { it[goalsKey] = goalsJson(goal) }
        prefs.failReads = true
        // Defaults while the store can't be read, so screens don't wait forever
        assertEquals(emptyList<Goal>(), repository.goals.await { true })
        prefs.failReads = false
        assertEquals(listOf("g"), repository.goals.await { it.isNotEmpty() }.map { it.id })
    }

    @Test
    fun aSessionStaysRecoverableWhenSavingItToHistoryFails() = test {
        val session = ChantState.start(goal = null, now = 1_000).copy(count = 5, lastUpdatedAt = 2_000)
        repository.saveActiveSession(session)

        prefs.failWrites = true
        repository.finishSession(session, endTime = 2_000)
        assertEquals(session.sessionId, repository.afterWrites { loadActiveSession() }?.sessionId)

        prefs.failWrites = false
        repository.finishSession(session, endTime = 2_000)
        assertNull(repository.afterWrites { loadActiveSession() })
        assertEquals(listOf(session.sessionId), repository.sessionHistory.await { it.isNotEmpty() }.map { it.id })
    }

    @Test
    fun afterWritesSeesEveryEarlierWrite() = test {
        val session = ChantState.start(goal = null, now = 0).copy(count = 3)
        val gate = CompletableDeferred<Unit>()
        repository.write {
            saveActiveSession(session)
            gate.await()
        }
        // Stop: saved to history and cleared, but still queued behind the slow write
        repository.finishSession(session, endTime = 10)

        val read = async { repository.afterWrites { loadActiveSession() } }
        delay(200)
        assertFalse(read.isCompleted)
        gate.complete(Unit)
        assertNull(read.await())

        // A failing block reaches the caller and doesn't stop the queue
        assertTrue(runCatching { repository.afterWrites<Int> { error("boom") } }.isFailure)
        assertEquals(1, repository.afterWrites { 1 })
    }

    @Test
    fun changingTheReminderMarksItHandledFromNow() = test {
        assertEquals(ReminderSettings() to 0L, repository.loadReminder())
        val before = System.currentTimeMillis()

        repository.setReminder(ReminderSettings(enabled = true, minuteOfDay = 7 * 60))
        val (settings, handled) = repository.loadReminder()
        assertEquals(ReminderSettings(enabled = true, minuteOfDay = 7 * 60), settings)
        assertTrue(handled >= before)

        repository.setReminderHandledThrough(5)
        repository.importBackup(Backup(settings = BackupSettings(reminderMinuteOfDay = 8 * 60)))
        assertTrue(repository.loadReminder().second >= before)
    }
}
