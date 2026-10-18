package com.superchant.data

/** Feedback to play after a count: what the count completed, if anything beyond a single bead. */
enum class CountFeedback { COUNT, ROUND, SECTION, GOAL }

data class CountResult(val state: ChantState, val feedback: CountFeedback)

/**
 * Pure counting rules shared by every input (volume keys and on-screen taps).
 * ChantingService applies the results and plays the feedback.
 */
object ChantCounter {

    /** Adds one count, rolling rounds every [ChantState.roundSize] beads and advancing goal mantras. Null when counting isn't allowed. */
    fun increment(state: ChantState): CountResult? {
        if (!state.isActive || state.isPaused || state.isGoalComplete) return null

        val newCount = state.count + 1
        val newRounds = newCount / state.roundSize
        val roundCompleted = newRounds > state.rounds

        val goal = state.activeGoal
        var mantraIndex = state.currentMantraIndex
        var mantraRounds = state.mantraRoundsCompleted
        var feedback = if (roundCompleted) CountFeedback.ROUND else CountFeedback.COUNT

        if (goal != null && roundCompleted) {
            mantraRounds++
            val mantra = goal.mantras.getOrNull(mantraIndex)
            if (mantra != null && mantraRounds >= mantra.targetRounds) {
                if (mantraIndex < goal.mantras.size - 1) {
                    feedback = CountFeedback.SECTION
                    mantraIndex++
                    mantraRounds = 0
                } else {
                    feedback = CountFeedback.GOAL
                }
            }
        }

        return CountResult(
            state.copy(
                count = newCount,
                rounds = newRounds,
                currentMantraIndex = mantraIndex,
                mantraRoundsCompleted = mantraRounds
            ),
            feedback
        )
    }

    /**
     * Removes the last count: the exact inverse of [increment], including stepping back across a round
     * boundary, re-opening a completed goal, or returning to the previous mantra. Null at 0 or while paused.
     */
    fun undo(state: ChantState): ChantState? {
        if (!state.isActive || state.isPaused || state.count <= 0) return null

        val newCount = state.count - 1
        val newRounds = newCount / state.roundSize

        val goal = state.activeGoal
        var mantraIndex = state.currentMantraIndex
        var mantraRounds = state.mantraRoundsCompleted

        if (goal != null && newRounds < state.rounds) {
            if (mantraRounds > 0) {
                mantraRounds--
            } else if (mantraIndex > 0) {
                // That round finished the previous mantra and advanced; step back into it.
                mantraIndex--
                mantraRounds = (goal.mantras[mantraIndex].targetRounds - 1).coerceAtLeast(0)
            }
        }

        return state.copy(
            count = newCount,
            rounds = newRounds,
            currentMantraIndex = mantraIndex,
            mantraRoundsCompleted = mantraRounds
        )
    }
}

/**
 * Drops repeat triggers that arrive within [minIntervalMs] of the last accepted one, e.g. a single
 * physical tap that the touchscreen reports twice.
 */
class CountDebouncer(private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS) {
    private var lastAcceptedAt: Long? = null

    fun tryAccept(nowMs: Long): Boolean {
        val last = lastAcceptedAt
        if (last != null && nowMs - last in 0 until minIntervalMs) return false
        lastAcceptedAt = nowMs
        return true
    }

    companion object {
        /** Well below the fastest realistic chant (~0.5 s per bead) and above touch double-fires. */
        const val DEFAULT_MIN_INTERVAL_MS = 150L
    }
}
