package com.superchant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val goal = Goal(
        id = "g1",
        name = "Morning",
        mantras = listOf(MantraGoal(id = "m1", name = "Om", targetRounds = 2)),
        createdAt = 10
    )
    private val record = SessionRecord(
        id = "s1", startTime = 100, endTime = 700, totalCounts = 120, totalRounds = 2,
        goalId = "g1", goalName = "Morning", mantras = listOf(MantraTally("Om", 2, 120))
    )
    private val backup = Backup(
        exportedAt = 1_000,
        settings = BackupSettings(themeMode = "DARK", dailyTarget = 4, beadClick = true),
        goals = listOf(goal),
        history = listOf(record, record.copy(id = "s2", startTime = 50, goalId = null, goalName = null, mantras = emptyList()))
    )

    @Test
    fun roundTripKeepsEverything() {
        val text = BackupCodec.encode(backup)
        assertEquals(backup, BackupCodec.decode(text))
        assertTrue("string ids", text.contains("\"id\": \"s1\""))
    }

    @Test
    fun legacyNumericIdsImportAsStrings() {
        val text = """
            {"app":"SuperChant","format":1,
             "goals":[{"id":1690000000000,"name":"Old","mantras":[{"id":1690000000001,"name":"A","targetRounds":1}]}],
             "history":[{"id":1700000000000,"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1,"goalId":1690000000000}]}
        """.trimIndent()
        val decoded = BackupCodec.decode(text)
        assertEquals("1690000000000", decoded.goals.single().id)
        assertEquals("1700000000000", decoded.history.single().id)
        assertEquals("1690000000000", decoded.history.single().goalId)
    }

    /** A file exported by v1.2 (#5), when round size, input mode and pocket guard were settings. */
    @Test
    fun exportsWithRemovedFieldsStillImport() {
        val text = """
            {"app":"SuperChant","format":1,"exportedAt":1000,
             "settings":{"themeMode":"DARK","inputMode":"BOTH","roundSize":54,"dailyTarget":4,"pocketGuard":false,"beadClick":true},
             "goals":[{"id":"g1","name":"Morning","mantras":[{"id":"m1","name":"Om","targetRounds":2}],"createdAt":10,"roundSize":54}],
             "history":[{"id":"s1","startTime":100,"endTime":700,"totalCounts":120,"totalRounds":2,"goalId":"g1","goalName":"Morning",
                         "roundSize":54,"mantras":[{"name":"Om","rounds":2,"counts":120}]}]}
        """.trimIndent()
        val decoded = BackupCodec.decode(text)
        assertEquals(BackupSettings(themeMode = "DARK", dailyTarget = 4, beadClick = true), decoded.settings)
        assertEquals(listOf(goal), decoded.goals)
        assertEquals(listOf(record), decoded.history)
    }

    @Test
    fun badEntriesAreSkippedNotFatal() {
        val text = """
            {"app":"SuperChant","format":1,"settings":"nonsense",
             "history":[{"id":"ok","startTime":1,"endTime":2,"totalCounts":1,"totalRounds":0},{"id":"bad"}],
             "goals":"not a list"}
        """.trimIndent()
        val decoded = BackupCodec.decode(text)
        assertEquals(listOf("ok"), decoded.history.map { it.id })
        assertEquals(emptyList<Goal>(), decoded.goals)
        assertEquals(BackupSettings(), decoded.settings)
    }

    @Test
    fun rejectsOtherFilesAndNewerFormats() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("[]") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"app":"Other","format":1}""") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"app":"SuperChant","format":99}""") }
    }

    @Test
    fun duplicateIdsInAFileAreMadeUnique() {
        val text = BackupCodec.encode(backup.copy(history = listOf(record, record)))
        assertEquals(2, BackupCodec.decode(text).history.map { it.id }.toSet().size)
    }

    @Test
    fun reimportingAFileWithDuplicateIdsAddsNothingNew() {
        val text = BackupCodec.encode(backup.copy(history = listOf(record, record.copy(startTime = 5))))
        val first = BackupCodec.mergeHistory(emptyList(), BackupCodec.decode(text).history)
        val second = BackupCodec.mergeHistory(first, BackupCodec.decode(text).history)
        assertEquals(2, first.size)
        assertEquals(first, second)
    }

    @Test
    fun mergeAddsUpdatesAndIsIdempotent() {
        val existing = listOf(record.copy(totalCounts = 5), record.copy(id = "local", startTime = 900))
        val merged = BackupCodec.mergeHistory(existing, backup.history)
        assertEquals(listOf("local", "s1", "s2"), merged.map { it.id })
        assertEquals(120, merged.first { it.id == "s1" }.totalCounts)
        assertEquals(merged, BackupCodec.mergeHistory(merged, backup.history))

        val goals = BackupCodec.mergeGoals(listOf(goal.copy(name = "Renamed"), goal.copy(id = "g2")), listOf(goal, goal.copy(id = "g3")))
        assertEquals(listOf("g3", "g1", "g2"), goals.map { it.id })
        assertEquals("Morning", goals[1].name)
        assertEquals(goals, BackupCodec.mergeGoals(goals, listOf(goal, goal.copy(id = "g3"))))
    }

    @Test
    fun restoreIntoEmptyAppGivesBackTheSameData() {
        val restored = BackupCodec.decode(BackupCodec.encode(backup))
        assertEquals(backup.history, BackupCodec.mergeHistory(emptyList(), restored.history))
        assertEquals(backup.goals, BackupCodec.mergeGoals(emptyList(), restored.goals))
    }
}
