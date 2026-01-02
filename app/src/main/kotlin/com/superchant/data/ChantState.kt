package com.superchant.data

import kotlinx.serialization.Serializable

/**
 * Represents the current state of a chanting session.
 * This is the single source of truth, owned by ChantingService.
 */
data class ChantState(
    val count: Int = 0,
    val rounds: Int = 0,
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val sessionStartTime: Long = 0L,
    // Goal-related state
    val activeGoal: Goal? = null,
    val currentMantraIndex: Int = 0,
    val mantraRoundsCompleted: Int = 0 // Rounds completed for current mantra
) {
    val progress: Float
        get() = if (COUNTS_PER_ROUND > 0) (count % COUNTS_PER_ROUND).toFloat() / COUNTS_PER_ROUND else 0f
    
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
    
    companion object {
        const val COUNTS_PER_ROUND = 108
    }
}

/**
 * Represents a single mantra with its target rounds.
 */
@Serializable
data class MantraGoal(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val targetRounds: Int
)

/**
 * Represents a goal with multiple mantras.
 */
@Serializable
data class Goal(
    val id: Long = System.currentTimeMillis(),
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
    val id: Long = System.currentTimeMillis(),
    val startTime: Long,
    val endTime: Long,
    val totalCounts: Int,
    val totalRounds: Int,
    val goalId: Long? = null,
    val goalName: String? = null
) {
    val durationMillis: Long
        get() = endTime - startTime
}

/**
 * User preferences for the app.
 */
@Serializable
data class UserPreferences(
    val themeMode: Int = 2, // 0 = Light, 1 = Dark, 2 = System
    val vibrationEnabled: Boolean = true,
    val countVibrationEnabled: Boolean = true, // Vibrate on every count
    val countVibrationIntensity: Int = 80, // 1-255, default light vibration
    val roundVibrationIntensity: Int = 255 // 1-255, default full intensity
)
