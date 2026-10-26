package com.superchant.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Golden fixtures for the Expo app (expo/src/core/__fixtures__/golden.json): stored strings and backup
 * files run through this app's decoders and encoders, so the TypeScript port can prove it reads and
 * writes the same data byte for byte. Regenerate with
 * `GOLDEN_WRITE=1 ./gradlew test --tests "com.superchant.data.GoldenFixturesTest" --rerun`; otherwise the
 * test fails when the committed fixtures no longer match what this code produces.
 */
class GoldenFixturesTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun <T> outcome(block: () -> T): Pair<T?, String?> = try {
        block() to null
    } catch (e: Exception) {
        null to e.javaClass.simpleName
    }

    private fun storedListCase(name: String, kind: String, input: String?): JsonObject {
        val result = when (kind) {
            "history" -> json.decodeStoredList(SessionRecord.serializer(), input).let { list ->
                val items = list.items.withUniqueRecordIds()
                Triple(items.map { json.encodeToString(SessionRecord.serializer(), it) }, list,
                    json.encodeStoredList(SessionRecord.serializer(), items, list.unreadable))
            }
            else -> json.decodeStoredList(Goal.serializer(), input).let { list ->
                val items = list.items.withUniqueGoalIds()
                Triple(items.map { json.encodeToString(Goal.serializer(), it) }, list,
                    json.encodeStoredList(Goal.serializer(), items, list.unreadable))
            }
        }
        return buildJsonObject {
            put("name", name)
            put("kind", kind)
            put("input", input?.let(::JsonPrimitive) ?: JsonNull)
            put("items", JsonArray(result.first.map(::JsonPrimitive)))
            put("unreadable", JsonArray(result.second.unreadable.map { JsonPrimitive(it.toString()) }))
            put("corrupt", result.second.corrupt)
            put("rewritten", result.third)
        }
    }

    private fun activeSessionCase(name: String, input: String): JsonObject {
        val (state, error) = outcome { json.decodeFromString(ChantState.serializer(), input).normalizedRounds() }
        return buildJsonObject {
            put("name", name)
            put("kind", "activeSession")
            put("input", input)
            put("output", state?.let { JsonPrimitive(json.encodeToString(ChantState.serializer(), it)) } ?: JsonNull)
            put("error", error?.let(::JsonPrimitive) ?: JsonNull)
        }
    }

    private fun backupCase(name: String, input: String): JsonObject {
        val (backup, error) = outcome { BackupCodec.decode(input) }
        return buildJsonObject {
            put("name", name)
            put("kind", "backup")
            put("input", input)
            put("output", backup?.let { JsonPrimitive(BackupCodec.encode(it)) } ?: JsonNull)
            put("error", error?.let(::JsonPrimitive) ?: JsonNull)
        }
    }

    private val record = """"startTime":1,"endTime":2,"totalCounts":3,"totalRounds":0"""

    private fun fixtures(): JsonArray = JsonArray(
        listOf(
            storedListCase("missing", "history", null),
            storedListCase("empty", "history", "[]"),
            storedListCase("corrupt text", "history", "not json"),
            storedListCase("empty string", "history", ""),
            storedListCase("object not array", "history", """{"a":1}"""),
            storedListCase("string not array", "history", "\"x\""),
            storedListCase("legacy numeric ids with a collision", "history", """
                [{"id":1700000000000,"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1,"goalId":1690000000000,"goalName":"Morning"},
                 {"id":1700000000000,"startTime":3,"endTime":4,"totalCounts":5,"totalRounds":0,"goalId":null,"goalName":null}]
            """.trimIndent()),
            storedListCase("broken element kept", "history", """[{"id":"a",$record},{"id":"broken"}]"""),
            storedListCase("quoted numbers", "history", """[{"id":"q","startTime":"1","endTime":2,"totalCounts":"3","totalRounds":0}]"""),
            storedListCase("decimal numbers", "history", """[{"id":"f","startTime":1.0,"endTime":2,"totalCounts":3,"totalRounds":0},{"id":"g","startTime":1,"endTime":2,"totalCounts":3.5,"totalRounds":0}]"""),
            storedListCase("exponents", "history", """[{"id":"e","startTime":1e3,"endTime":2E3,"totalCounts":3,"totalRounds":0},{"id":"e2","startTime":1,"endTime":2,"totalCounts":1e2,"totalRounds":0},{"id":"e3","startTime":1e-3,"endTime":2,"totalCounts":1,"totalRounds":0}]"""),
            storedListCase("id kinds", "history", """[{"id":true,$record},{"id":1.5,$record},{"id":{"x":1},$record},{"id":null,$record},{"id":[1],$record},{"id":-0,$record}]"""),
            storedListCase("goal id and name kinds", "history", """[{"id":"a",$record,"goalId":7,"goalName":null},{"id":"b",$record,"goalId":{"x":1}},{"id":"c",$record,"goalName":5},{"id":"d",$record,"goalName":"x","goalId":false}]"""),
            storedListCase("mantras kinds", "history", """[{"id":"a",$record,"mantras":null},{"id":"b",$record,"mantras":[{"name":"Om","rounds":1,"counts":108}]},{"id":"c",$record,"mantras":[{"name":"Om","rounds":1}]},{"id":"d",$record,"mantras":{}}]"""),
            storedListCase("int overflow", "history", """[{"id":"o","startTime":1,"endTime":2,"totalCounts":3000000000,"totalRounds":0},{"id":"p","startTime":30000000000,"endTime":30000000001,"totalCounts":2147483647,"totalRounds":-2147483648}]"""),
            storedListCase("unquoted garbage", "history", """[{"id":abc,$record},{"id":"z","startTime":01,"endTime":2,"totalCounts":3,"totalRounds":0}]"""),
            storedListCase("booleans and nulls in numbers", "history", """[{"id":"b","startTime":true,"endTime":2,"totalCounts":3,"totalRounds":0},{"id":"n","startTime":null,"endTime":2,"totalCounts":3,"totalRounds":0}]"""),
            storedListCase("escapes and unicode", "history", """[{"id":"ué\n\"\\\/","startTime":1,"endTime":2,"totalCounts":3,"totalRounds":0,"goalName":"ॐ नमः \t tab \u0001 ctl 😀"}]"""),
            storedListCase("duplicate keys", "history", """[{"id":"d1","id":"d2",$record,"totalCounts":9}]"""),
            storedListCase("unknown keys and whitespace", "history", "[ \n\t{\"id\" : \"w\" , \"roundSize\" : 54, \"extra\":{\"a\":[1,2]},$record} ]"),
            storedListCase("many duplicates", "history", "[" + (1..6).joinToString(",") { """{"id":"x",$record}""" } + """,{"id":"x-2",$record}]"""),
            storedListCase("non-object elements", "history", """[1,"s",null,[],{"id":"ok",$record}]"""),
            storedListCase("odd literals written back", "history", """[{"id":"w","startTime":01,"endTime":True,"totalCounts":abc,"totalRounds":2.50,"a":18446744073709551615,"b":1e-10,"c":1e7,"d":-0,"e":+5,"f":12345678.9,"g":0.0001,"h":FALSE}]"""),
            storedListCase("goals", "goals", """[{"id":5,"name":"G","mantras":[{"id":7,"name":"M","targetRounds":3}],"createdAt":9},{"id":"g","name":"Dup mantras","mantras":[{"id":"m","name":"A","targetRounds":1},{"id":"m","name":"B","targetRounds":2}],"createdAt":1}]"""),
            storedListCase("goal duplicates and broken", "goals", """[{"id":"g","name":"A","mantras":[],"createdAt":1},{"id":"g","name":"B","mantras":[],"createdAt":2},{"id":"bad","mantras":[],"createdAt":3},{"id":"bad2","name":"x","mantras":[{"id":"m","name":"A"}],"createdAt":3},{"id":"n","name":null,"mantras":[],"createdAt":3}]"""),
            storedListCase("goal invalid but stored", "goals", """[{"id":"z","name":"  ","mantras":[{"id":"m","name":"","targetRounds":0}],"createdAt":5,"roundSize":27}]"""),
            activeSessionCase("fresh free session", """{"count":5,"rounds":0,"isActive":true,"isPaused":false,"sessionStartTime":100,"touchCounting":true,"activeGoal":null,"currentMantraIndex":0,"mantraRoundsCompleted":0,"sessionId":"s","lastUpdatedAt":200}"""),
            activeSessionCase("older version with round size 27", """{"count":30,"rounds":1,"isActive":true,"sessionStartTime":5,"roundSize":27,"sessionId":"s","lastUpdatedAt":9}"""),
            activeSessionCase("legacy goal session at 54", """{"count":250,"rounds":4,"isActive":true,"activeGoal":{"id":1,"name":"G","mantras":[{"id":2,"name":"A","targetRounds":1},{"id":3,"name":"B","targetRounds":2}],"createdAt":1},"currentMantraIndex":1,"mantraRoundsCompleted":3,"sessionId":"s","lastUpdatedAt":9,"inputMode":"BOTH"}"""),
            activeSessionCase("zero target mantras", """{"count":400,"isActive":true,"activeGoal":{"id":"g","name":"G","mantras":[{"id":"a","name":"A","targetRounds":0},{"id":"b","name":"B","targetRounds":0}],"createdAt":1},"sessionId":"s"}"""),
            activeSessionCase("paused as string", """{"count":1,"isActive":true,"isPaused":"true","sessionId":"s"}"""),
            activeSessionCase("missing everything", "{}"),
            activeSessionCase("not an object", "[1]"),
            activeSessionCase("session id number", """{"count":1,"isActive":true,"sessionId":5}"""),
            backupCase("full", """{"app":"SuperChant","format":1,"exportedAt":1000,"settings":{"themeMode":"DARK","dailyTarget":4,"countVibration":false,"countIntensity":80,"roundVibration":true,"roundIntensity":255,"milestonePulse":true,"beadClick":true,"reminderEnabled":true,"reminderMinuteOfDay":360},"goals":[{"id":"g1","name":"Morning","mantras":[{"id":"m1","name":"Om","targetRounds":2}],"createdAt":10}],"history":[{"id":"s1","startTime":100,"endTime":700,"totalCounts":120,"totalRounds":2,"goalId":"g1","goalName":"Morning","mantras":[{"name":"Om","rounds":2,"counts":120}]}]}"""),
            backupCase("v1.2 removed fields", """{"app":"SuperChant","format":1,"exportedAt":1000,"settings":{"themeMode":"DARK","inputMode":"BOTH","roundSize":54,"dailyTarget":4,"pocketGuard":false,"beadClick":true},"goals":[{"id":"g1","name":"Morning","mantras":[{"id":"m1","name":"Om","targetRounds":2}],"createdAt":10,"roundSize":54}],"history":[]}"""),
            backupCase("legacy numeric ids", """{"app":"SuperChant","format":1,"goals":[{"id":1690000000000,"name":"Old","mantras":[{"id":1690000000001,"name":"A","targetRounds":1}],"createdAt":1}],"history":[{"id":1700000000000,"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1,"goalId":1690000000000},{"id":1700000000000,"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1}]}"""),
            backupCase("content ids", """{"app":"SuperChant","format":1,"goals":[{"name":"By hand ॐ","mantras":[{"name":"Om","targetRounds":1},{"id":null,"name":"Ram \"x\"\n","targetRounds":2.0e0}],"createdAt":1},{"name":"Second","mantras":[{"name":"Om","targetRounds":1}],"createdAt":1.0}],"history":[{"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1},{"id":null,"startTime":3,"endTime":4,"totalCounts":5,"totalRounds":0,"extra":{"b":[1,2.50,true,null,"é"]}},{"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1},{"startTime":1e1,"endTime":2E1,"totalCounts":1,"totalRounds":0,"goalName":"tab\there 😀"}]}"""),
            backupCase("bad entries", """{"app":"SuperChant","format":1,"settings":"nonsense","history":[{"id":"ok","startTime":1,"endTime":2,"totalCounts":1,"totalRounds":0},{"id":"bad"}],"goals":"not a list"}"""),
            backupCase("invalid goals skipped", """{"app":"SuperChant","format":1,"goals":[{"id":"ok","name":"Fine","mantras":[{"id":"m","name":"Om","targetRounds":999}],"createdAt":1},{"id":"blank","name":"  ","mantras":[{"id":"m","name":"Om","targetRounds":1}],"createdAt":1},{"id":"none","name":"No mantras","mantras":[],"createdAt":1},{"id":"huge","name":"Huge","mantras":[{"id":"m","name":"Om","targetRounds":1000}],"createdAt":1}]}"""),
            backupCase("settings partly wrong", """{"app":"SuperChant","format":1,"settings":{"themeMode":"LIGHT","dailyTarget":"4","beadClick":"yes"}}"""),
            backupCase("settings with nulls", """{"app":"SuperChant","format":1,"settings":{"themeMode":null,"dailyTarget":7,"reminderMinuteOfDay":5000}}"""),
            backupCase("format quoted", """{"app":"SuperChant","format":"1","exportedAt":"55"}"""),
            backupCase("format decimal", """{"app":"SuperChant","format":1.0}"""),
            backupCase("format zero", """{"app":"SuperChant","format":0,"exportedAt":1.5}"""),
            backupCase("newer format", """{"app":"SuperChant","format":2}"""),
            backupCase("other app", """{"app":"Other","format":1}"""),
            backupCase("missing format", """{"app":"SuperChant"}"""),
            backupCase("array", "[]"),
            backupCase("not json", "not json"),
            backupCase("duplicate history ids", """{"app":"SuperChant","format":1,"history":[{"id":"s",$record},{"id":"s",$record},{"id":"s-2",$record}]}"""),
        )
    )

    @Test
    fun fixturesMatchThisCode() {
        val file = File("../expo/src/core/__fixtures__/golden.json")
        val text = Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), fixtures()) + "\n"
        if (System.getenv("GOLDEN_WRITE") == "1") {
            file.parentFile.mkdirs()
            file.writeText(text)
        }
        assertEquals("Regenerate with GOLDEN_WRITE=1", file.readText(), text)
    }
}
