package com.superchant.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * A complete, human-readable copy of the user's data: settings, goals and history. Written and read
 * through the Storage Access Framework, so the user decides where it lives; nothing leaves the device
 * otherwise. IDs are strings (see [LenientIdSerializer]); older numeric IDs still import, and fields
 * older versions wrote that no longer exist (e.g. `roundSize`, `inputMode`, `pocketGuard`) are ignored.
 */
@Serializable
data class Backup(
    val app: String = APP,
    val format: Int = FORMAT,
    val exportedAt: Long = 0L,
    val settings: BackupSettings = BackupSettings(),
    val goals: List<Goal> = emptyList(),
    val history: List<SessionRecord> = emptyList()
) {
    companion object {
        const val APP = "SuperChant"
        const val FORMAT = 1
        const val MIME_TYPE = "application/json"
    }
}

/** Settings worth carrying to a new phone. Null leaves the current value unchanged on import. */
@Serializable
data class BackupSettings(
    val themeMode: String? = null,
    val dailyTarget: Int? = null,
    val countVibration: Boolean? = null,
    val countIntensity: Int? = null,
    val roundVibration: Boolean? = null,
    val roundIntensity: Int? = null,
    val milestonePulse: Boolean? = null,
    val beadClick: Boolean? = null,
    val reminderEnabled: Boolean? = null,
    val reminderMinuteOfDay: Int? = null
)

class BackupFormatException(message: String) : Exception(message)

/** What an import changed, for the confirmation message. */
data class ImportResult(val sessionsAdded: Int, val goalsAdded: Int, val goalsUpdated: Int)

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /**
     * Reads a backup, skipping individual goals or sessions that don't decode (or goals the editor couldn't
     * make, see [Goal.isValid]) instead of rejecting the whole file. Throws [BackupFormatException] if this
     * isn't a SuperChant backup at all, or is from a newer format this version can't understand.
     */
    fun decode(text: String): Backup {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw BackupFormatException("Not a JSON object")
        if (root["app"]?.jsonPrimitive?.contentOrNull != Backup.APP) throw BackupFormatException("Not a SuperChant backup")
        val format = root["format"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: throw BackupFormatException("Missing format")
        if (format > Backup.FORMAT) throw BackupFormatException("Made by a newer version (format $format)")

        val settings = root["settings"]?.let {
            runCatching { json.decodeFromJsonElement(BackupSettings.serializer(), it) }.getOrNull()
        } ?: BackupSettings()
        return Backup(
            format = format,
            exportedAt = root["exportedAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            settings = settings,
            goals = decodeEach(root["goals"]) { goal ->
                val withIds = (goal.withContentId() as? JsonObject)?.let { obj ->
                    val mantras = obj["mantras"] as? JsonArray ?: return@let obj
                    JsonObject(obj + ("mantras" to JsonArray(mantras.map { it.withContentId() })))
                } ?: goal
                json.decodeFromJsonElement(Goal.serializer(), withIds).takeIf { it.isValid }
            }.withUniqueGoalIds(),
            history = decodeEach(root["history"]) { json.decodeFromJsonElement(SessionRecord.serializer(), it.withContentId()) }
                .withUniqueRecordIds()
        )
    }

    private fun <T : Any> decodeEach(element: JsonElement?, decode: (JsonElement) -> T?): List<T> =
        (element as? JsonArray).orEmpty().mapNotNull { runCatching { decode(it) }.getOrNull() }

    /**
     * An entry written without an ID (e.g. by hand) gets one derived from its content rather than a random
     * one, so importing the same file twice updates it instead of adding a copy.
     */
    private fun JsonElement.withContentId(): JsonElement {
        if (this !is JsonObject || (get("id") ?: JsonNull) != JsonNull) return this
        return JsonObject(this + ("id" to JsonPrimitive(UUID.nameUUIDFromBytes(toString().toByteArray()).toString())))
    }

    /** Sessions from both, newest first; on an ID clash the imported copy wins. Nothing is dropped. */
    fun mergeHistory(existing: List<SessionRecord>, imported: List<SessionRecord>): List<SessionRecord> {
        val importedIds = imported.mapTo(HashSet()) { it.id }
        return (imported + existing.filterNot { it.id in importedIds })
            .sortedByDescending { it.startTime }
    }

    /** Imported goals replace goals with the same ID in place; new ones go on top in file order. */
    fun mergeGoals(existing: List<Goal>, imported: List<Goal>): List<Goal> {
        val byId = imported.associateBy { it.id }
        val existingIds = existing.mapTo(HashSet()) { it.id }
        return imported.filterNot { it.id in existingIds } + existing.map { byId[it.id] ?: it }
    }

    fun suggestedFileName(date: java.time.LocalDate): String = "superchant-backup-$date.json"
}
