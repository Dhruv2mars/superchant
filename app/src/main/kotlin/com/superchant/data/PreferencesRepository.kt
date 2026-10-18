package com.superchant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.superchant.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "superchant_prefs")

class PreferencesRepository(private val context: Context) {
    
    private object PreferencesKeys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val COUNT_VIBRATION_ENABLED = booleanPreferencesKey("count_vibration_enabled")
        val COUNT_VIBRATION_INTENSITY = intPreferencesKey("count_vibration_intensity")
        val ROUND_VIBRATION_INTENSITY = intPreferencesKey("round_vibration_intensity")
        val SESSION_HISTORY = stringPreferencesKey("session_history")
        val SAVED_GOALS = stringPreferencesKey("saved_goals")
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    // Theme Mode
    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        when (preferences[PreferencesKeys.THEME_MODE] ?: 2) {
            0 -> ThemeMode.LIGHT
            1 -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }
    
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = when (mode) {
                ThemeMode.LIGHT -> 0
                ThemeMode.DARK -> 1
                ThemeMode.SYSTEM -> 2
            }
        }
    }
    
    // Vibration settings
    val vibrationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.VIBRATION_ENABLED] ?: true
    }
    
    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIBRATION_ENABLED] = enabled
        }
    }
    
    val countVibrationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COUNT_VIBRATION_ENABLED] ?: true
    }
    
    suspend fun setCountVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COUNT_VIBRATION_ENABLED] = enabled
        }
    }
    
    // Vibration intensity settings (1-255)
    val countVibrationIntensityFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COUNT_VIBRATION_INTENSITY] ?: 80 // Default light vibration
    }
    
    suspend fun setCountVibrationIntensity(intensity: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COUNT_VIBRATION_INTENSITY] = intensity.coerceIn(1, 255)
        }
    }
    
    val roundVibrationIntensityFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ROUND_VIBRATION_INTENSITY] ?: 255 // Default full intensity
    }
    
    suspend fun setRoundVibrationIntensity(intensity: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ROUND_VIBRATION_INTENSITY] = intensity.coerceIn(1, 255)
        }
    }
    
    // Session history
    val sessionHistoryFlow: Flow<List<SessionRecord>> = context.dataStore.data.map { preferences ->
        decodeList<SessionRecord>(preferences[PreferencesKeys.SESSION_HISTORY])
    }
    
    suspend fun addSessionRecord(record: SessionRecord) {
        // Most recent first; keep only the last 100 sessions
        updateList<SessionRecord>(PreferencesKeys.SESSION_HISTORY) { (listOf(record) + it).take(100) }
    }
    
    suspend fun clearSessionHistory() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SESSION_HISTORY] = "[]"
        }
    }
    
    // Goals
    val goalsFlow: Flow<List<Goal>> = context.dataStore.data.map { preferences ->
        decodeList<Goal>(preferences[PreferencesKeys.SAVED_GOALS])
    }
    
    /** Replaces the goal with the same ID, or adds it at the top. */
    suspend fun saveGoal(goal: Goal) {
        updateList<Goal>(PreferencesKeys.SAVED_GOALS) { goals ->
            if (goals.any { it.id == goal.id }) goals.map { if (it.id == goal.id) goal else it } else listOf(goal) + goals
        }
    }
    
    suspend fun deleteGoal(goalId: Long) {
        updateList<Goal>(PreferencesKeys.SAVED_GOALS) { goals -> goals.filterNot { it.id == goalId } }
    }
    
    /** Decodes a JSON list, treating missing or unreadable data as empty. */
    private inline fun <reified T> decodeList(stored: String?): List<T> =
        try {
            json.decodeFromString<List<T>>(stored ?: "[]")
        } catch (e: Exception) {
            emptyList()
        }
    
    private suspend inline fun <reified T> updateList(
        key: Preferences.Key<String>,
        crossinline transform: (List<T>) -> List<T>
    ) {
        context.dataStore.edit { preferences ->
            preferences[key] = json.encodeToString(transform(decodeList<T>(preferences[key])))
        }
    }
}
