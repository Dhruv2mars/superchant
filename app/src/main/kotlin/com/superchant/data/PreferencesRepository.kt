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
import kotlinx.serialization.encodeToString
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
        val historyJson = preferences[PreferencesKeys.SESSION_HISTORY] ?: "[]"
        try {
            json.decodeFromString<List<SessionRecord>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun addSessionRecord(record: SessionRecord) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[PreferencesKeys.SESSION_HISTORY] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<SessionRecord>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            // Add new record at the beginning (most recent first)
            currentList.add(0, record)
            // Keep only last 100 sessions
            val trimmedList = currentList.take(100)
            preferences[PreferencesKeys.SESSION_HISTORY] = json.encodeToString(trimmedList)
        }
    }
    
    suspend fun clearSessionHistory() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SESSION_HISTORY] = "[]"
        }
    }
    
    // Goals
    val goalsFlow: Flow<List<Goal>> = context.dataStore.data.map { preferences ->
        val goalsJson = preferences[PreferencesKeys.SAVED_GOALS] ?: "[]"
        try {
            json.decodeFromString<List<Goal>>(goalsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun saveGoal(goal: Goal) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[PreferencesKeys.SAVED_GOALS] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<Goal>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            // Check if goal with same ID exists, replace it; otherwise add
            val existingIndex = currentList.indexOfFirst { it.id == goal.id }
            if (existingIndex >= 0) {
                currentList[existingIndex] = goal
            } else {
                currentList.add(0, goal)
            }
            preferences[PreferencesKeys.SAVED_GOALS] = json.encodeToString(currentList)
        }
    }
    
    suspend fun deleteGoal(goalId: Long) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[PreferencesKeys.SAVED_GOALS] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<Goal>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            currentList.removeAll { it.id == goalId }
            preferences[PreferencesKeys.SAVED_GOALS] = json.encodeToString(currentList)
        }
    }
}
