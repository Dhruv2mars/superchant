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
    /** Whether on-screen taps count. Volume buttons count either way (when the service is enabled). */
    val touchCounting: Boolean = false,
    // Goal-related state
    val activeGoal: Goal? = null,
    val currentMantraIndex: Int = 0,
    val mantraRoundsCompleted: Int = 0, // Rounds completed for current mantra
    // Identity of the session; becomes the SessionRecord id so saving is idempotent.
    val sessionId: String = "",
    val lastUpdatedAt: Long = 0L
) {
    val progress: Float
        get() = currentRoundCount.toFloat() / ROUND_SIZE

    val currentRoundCount: Int
        get() = count % ROUND_SIZE

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
        goalName = activeGoal?.name,
        mantras = mantraTallies()
    )

    /** What was chanted of each goal mantra so far; empty without a goal. Counts add up to [count]. */
    fun mantraTallies(): List<MantraTally> {
        val goal = activeGoal ?: return emptyList()
        return goal.mantras.mapIndexedNotNull { index, mantra ->
            val rounds = when {
                index < currentMantraIndex -> mantra.targetRounds
                index == currentMantraIndex -> mantraRoundsCompleted
                else -> 0
            }
            val partial = if (index == currentMantraIndex) currentRoundCount else 0
            val counts = rounds * ROUND_SIZE + partial
            if (counts > 0) MantraTally(mantra.name, rounds, counts) else null
        }
    }

    /**
     * Rounds and goal progress recomputed from [count] at 108 beads, for a session persisted by an older
     * version that allowed other round sizes. A session already counted at 108 comes back unchanged.
     */
    fun normalizedRounds(): ChantState {
        val totalRounds = count / ROUND_SIZE
        val mantras = activeGoal?.mantras.orEmpty()
        var index = 0
        var remaining = totalRounds
        while (index < mantras.size - 1 && remaining >= mantras[index].targetRounds.coerceAtLeast(1)) {
            remaining -= mantras[index].targetRounds.coerceAtLeast(1)
            index++
        }
        return if (mantras.isEmpty()) copy(rounds = totalRounds)
        else copy(rounds = totalRounds, currentMantraIndex = index, mantraRoundsCompleted = remaining)
    }

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
        const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L

        /** Beads per round: a traditional mala. */
        const val ROUND_SIZE = 108

        /** A milestone pulse every 27 beads, the marker beads that quarter a 108 mala. */
        const val MILESTONE_INTERVAL = 27

        fun start(goal: Goal?, now: Long, touchCounting: Boolean = false) = ChantState(
            isActive = true,
            sessionStartTime = now,
            touchCounting = touchCounting,
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

    /** What the goal editor allows: a name, and at least one named mantra with 1 to [MAX_TARGET_ROUNDS] rounds. */
    val isValid: Boolean
        get() = name.isNotBlank() && mantras.isNotEmpty() &&
            mantras.all { it.name.isNotBlank() && it.targetRounds in 1..MAX_TARGET_ROUNDS }

    companion object {
        const val MAX_TARGET_ROUNDS = 999
    }
}

/** Rounds and counts of one mantra within a session. */
@Serializable
data class MantraTally(
    val name: String,
    val rounds: Int,
    val counts: Int
)

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
    val goalName: String? = null,
    /** Per-mantra breakdown for goal sessions; empty for free sessions and older records. */
    val mantras: List<MantraTally> = emptyList()
) {
    val durationMillis: Long
        get() = (endTime - startTime).coerceAtLeast(0)
}
