package com.superchant.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** Random, collision-free ID for goals, mantras and sessions. */
fun newId(): String = UUID.randomUUID().toString()

/**
 * Represents the current state of a chanting session.
 * This is the single source of truth, owned by ChantingService, and is persisted while active
 * so a session survives process death.
 */
@Serializable
data class ChantState(
    val count: Int = 0,
    val rounds: Int = 0,
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val sessionStartTime: Long = 0L,
    // Goal-related state
    val activeGoal: Goal? = null,
    val currentMantraIndex: Int = 0,
    val mantraRoundsCompleted: Int = 0, // Rounds completed for current mantra
    // Identity of the session; becomes the SessionRecord id so saving is idempotent.
    val sessionId: String = "",
    val lastUpdatedAt: Long = 0L
) {
    val progress: Float
        get() = currentRoundCount.toFloat() / COUNTS_PER_ROUND

    val currentRoundCount: Int
        get() = count % COUNTS_PER_ROUND

    // Get current mantra from goal, if any
    val currentMantra: MantraGoal?
        get() = activeGoal?.mantras?.getOrNull(currentMantraIndex)

    // Check if current mantra section is complete
    val isCurrentMantraComplete: Boolean
        get() {
            val mantra = currentMantra ?: return false
            return mantraRoundsCompleted >= mantra.targetRounds
        }

    // Check if entire goal is complete
    val isGoalComplete: Boolean
        get() {
            val goal = activeGoal ?: return false
            return currentMantraIndex >= goal.mantras.size - 1 && isCurrentMantraComplete
        }

    /** History record for this session, ending at [endTime]. */
    fun toRecord(endTime: Long) = SessionRecord(
        id = sessionId.ifEmpty { newId() },
        startTime = sessionStartTime,
        endTime = maxOf(endTime, sessionStartTime),
        totalCounts = count,
        totalRounds = rounds,
        goalId = activeGoal?.id,
        goalName = activeGoal?.name
    )

    /**
     * Decides what to do with a session persisted by a process that no longer exists: resume it,
     * or, if it was last touched more than [STALE_AFTER_MS] ago (e.g. before a reboot), save it to
     * history as ending at its last activity.
     */
    fun restoreAction(now: Long): RestoreAction = when {
        !isActive -> RestoreAction.Discard
        now - lastUpdatedAt > STALE_AFTER_MS ->
            if (count > 0) RestoreAction.Finish(toRecord(lastUpdatedAt)) else RestoreAction.Discard
        else -> RestoreAction.Resume(this)
    }

    companion object {
        const val COUNTS_PER_ROUND = 108
        const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L

        fun start(goal: Goal?, now: Long) = ChantState(
            isActive = true,
            sessionStartTime = now,
            activeGoal = goal,
            sessionId = newId(),
            lastUpdatedAt = now
        )
    }
}

sealed interface RestoreAction {
    data class Resume(val state: ChantState) : RestoreAction
    data class Finish(val record: SessionRecord) : RestoreAction
    data object Discard : RestoreAction
}

/**
 * Reads IDs stored as either JSON numbers (v1.1.0 and earlier used currentTimeMillis) or strings,
 * and always writes strings.
 */
object LenientIdSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("com.superchant.LenientId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String =
        if (decoder is JsonDecoder) decoder.decodeJsonElement().jsonPrimitive.content else decoder.decodeString()

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/**
 * Represents a single mantra with its target rounds.
 */
@Serializable
data class MantraGoal(
    @Serializable(with = LenientIdSerializer::class)
    val id: String = newId(),
    val name: String,
    val targetRounds: Int
)

/**
 * Represents a goal with multiple mantras.
 */
@Serializable
data class Goal(
    @Serializable(with = LenientIdSerializer::class)
    val id: String = newId(),
    val name: String,
    val mantras: List<MantraGoal>,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalRounds: Int
        get() = mantras.sumOf { it.targetRounds }
}

/**
 * Represents a completed chanting session for history.
 */
@Serializable
data class SessionRecord(
    @Serializable(with = LenientIdSerializer::class)
    val id: String = newId(),
    val startTime: Long,
    val endTime: Long,
    val totalCounts: Int,
    val totalRounds: Int,
    @Serializable(with = LenientIdSerializer::class)
    val goalId: String? = null,
    val goalName: String? = null
) {
    val durationMillis: Long
        get() = (endTime - startTime).coerceAtLeast(0)
}
