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
    /** Beads per round for this session, fixed when it starts (see [RoundSize]). */
    val roundSize: Int = RoundSize.DEFAULT,
    // Goal-related state
    val activeGoal: Goal? = null,
    val currentMantraIndex: Int = 0,
    val mantraRoundsCompleted: Int = 0, // Rounds completed for current mantra
    // Identity of the session; becomes the SessionRecord id so saving is idempotent.
    val sessionId: String = "",
    val lastUpdatedAt: Long = 0L
) {
    val progress: Float
        get() = currentRoundCount.toFloat() / roundSize

    val currentRoundCount: Int
        get() = count % roundSize

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
        roundSize = roundSize
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
        const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L

        fun start(goal: Goal?, now: Long, roundSize: Int = RoundSize.DEFAULT) = ChantState(
            isActive = true,
            sessionStartTime = now,
            roundSize = RoundSize.sanitize(roundSize),
            activeGoal = goal,
            sessionId = newId(),
            lastUpdatedAt = now
        )
    }
}

/** Beads per round. Traditional malas have 108 beads; wrist and travel malas often have 54 or 27. */
object RoundSize {
    const val DEFAULT = 108
    const val MIN = 1
    const val MAX = 1008
    val PRESETS = listOf(27, 54, 108)

    fun isValid(size: Int): Boolean = size in MIN..MAX

    /** Out-of-range values (e.g. from an edited backup) fall back to the default. */
    fun sanitize(size: Int?): Int = size?.takeIf(::isValid) ?: DEFAULT

    /** The size a session uses: the goal's own size if it has a valid one, else the app setting. */
    fun forSession(goal: Goal?, appDefault: Int): Int = sanitize(goal?.roundSize?.takeIf(::isValid) ?: appDefault)

    /**
     * Counts between milestone pulses within a round, or 0 for none: every 27 beads where the round is
     * a multiple of 27 (the marker beads on a 108 or 54 mala), else quarters of rounds divisible by 4.
     */
    fun milestoneInterval(size: Int): Int = when {
        size > 27 && size % 27 == 0 -> 27
        size >= 16 && size % 4 == 0 -> size / 4
        else -> 0
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
    val createdAt: Long = System.currentTimeMillis(),
    /** Beads per round for this goal; null follows the app setting. */
    val roundSize: Int? = null
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
    val goalName: String? = null,
    val roundSize: Int = RoundSize.DEFAULT
) {
    val durationMillis: Long
        get() = (endTime - startTime).coerceAtLeast(0)
}
