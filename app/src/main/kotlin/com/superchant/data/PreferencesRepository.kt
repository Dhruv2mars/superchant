package com.superchant.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.superchant.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "superchant_prefs")

// Separate small store so the per-count session snapshot doesn't rewrite history and goals.
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "active_session")

/** Optional extras around each count. */
data class CountingFeel(
    /** A distinct pulse at milestone beads (see [RoundSize.milestoneInterval]). */
    val milestonePulse: Boolean = true,
    /** A soft synthesized bead click on each count; plays through the system sounds volume. */
    val beadClick: Boolean = false,
    /** Ignore on-screen taps while the proximity sensor is covered, e.g. in a pocket. */
    val pocketGuard: Boolean = true
)

/** The optional daily reminder; [minuteOfDay] is local time (e.g. 360 = 6:00). */
data class ReminderSettings(
    val enabled: Boolean = false,
    val minuteOfDay: Int = 6 * 60
)

data class VibrationSettings(
    val roundEnabled: Boolean = true,
    val countEnabled: Boolean = true,
    val countIntensity: Int = 80,
    val roundIntensity: Int = 255
)

/**
 * Process-wide access to settings, goals, history and the active session.
 *
 * Values are exposed as StateFlows that start loading when the process starts and are null until
 * the first read completes, so screens can tell "loading" from "empty". Writes go through [write],
 * which runs them one at a time, in order, on an app-wide scope that outlives screens and services.
 */
class PreferencesRepository private constructor(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val COUNT_VIBRATION_ENABLED = booleanPreferencesKey("count_vibration_enabled")
        val COUNT_VIBRATION_INTENSITY = intPreferencesKey("count_vibration_intensity")
        val ROUND_VIBRATION_INTENSITY = intPreferencesKey("round_vibration_intensity")
        val SESSION_HISTORY = stringPreferencesKey("session_history")
        val SAVED_GOALS = stringPreferencesKey("saved_goals")
        val ACTIVE_SESSION = stringPreferencesKey("active_session")
        val INPUT_MODE = stringPreferencesKey("input_mode")
        val ROUND_SIZE = intPreferencesKey("round_size")
        val MILESTONE_PULSE = booleanPreferencesKey("milestone_pulse")
        val BEAD_CLICK = booleanPreferencesKey("bead_click")
        val POCKET_GUARD = booleanPreferencesKey("pocket_guard")
        val ACCESSIBILITY_CONSENT = booleanPreferencesKey("accessibility_consent")
        val DAILY_TARGET = intPreferencesKey("daily_target")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writes = Channel<suspend PreferencesRepository.() -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (block in writes) {
                try {
                    block()
                } catch (e: Exception) {
                    Log.e(TAG, "Write failed", e)
                }
            }
        }
    }

    /** Queues [block]; queued writes run sequentially in submission order. */
    fun write(block: suspend PreferencesRepository.() -> Unit) {
        writes.trySend(block)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // A read error (e.g. IOException) shows defaults instead of crashing every collector.
    private val prefs: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) {
            Log.e(TAG, "Reading preferences failed", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    private fun <T> Flow<T>.share(): StateFlow<T?> = distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    // Theme Mode
    val themeMode: StateFlow<ThemeMode?> = prefs.map { preferences ->
        when (preferences[PreferencesKeys.THEME_MODE]) {
            0 -> ThemeMode.LIGHT
            1 -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }.share()

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
    val vibrationSettings: StateFlow<VibrationSettings?> = prefs.map { preferences ->
        val defaults = VibrationSettings()
        VibrationSettings(
            roundEnabled = preferences[PreferencesKeys.VIBRATION_ENABLED] ?: defaults.roundEnabled,
            countEnabled = preferences[PreferencesKeys.COUNT_VIBRATION_ENABLED] ?: defaults.countEnabled,
            countIntensity = preferences[PreferencesKeys.COUNT_VIBRATION_INTENSITY] ?: defaults.countIntensity,
            roundIntensity = preferences[PreferencesKeys.ROUND_VIBRATION_INTENSITY] ?: defaults.roundIntensity
        )
    }.share()

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun setCountVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COUNT_VIBRATION_ENABLED] = enabled
        }
    }

    // Vibration intensity settings (1-255)
    suspend fun setCountVibrationIntensity(intensity: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COUNT_VIBRATION_INTENSITY] = intensity.coerceIn(1, 255)
        }
    }

    suspend fun setRoundVibrationIntensity(intensity: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ROUND_VIBRATION_INTENSITY] = intensity.coerceIn(1, 255)
        }
    }

    val countingFeel: StateFlow<CountingFeel?> = prefs.map { preferences ->
        val defaults = CountingFeel()
        CountingFeel(
            milestonePulse = preferences[PreferencesKeys.MILESTONE_PULSE] ?: defaults.milestonePulse,
            beadClick = preferences[PreferencesKeys.BEAD_CLICK] ?: defaults.beadClick,
            pocketGuard = preferences[PreferencesKeys.POCKET_GUARD] ?: defaults.pocketGuard
        )
    }.share()

    suspend fun setMilestonePulse(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.MILESTONE_PULSE] = enabled }
    }

    suspend fun setBeadClick(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BEAD_CLICK] = enabled }
    }

    suspend fun setPocketGuard(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.POCKET_GUARD] = enabled }
    }

    /** Rounds per day to aim for; 0 means no target. */
    val dailyTarget: StateFlow<Int?> = prefs.map {
        (it[PreferencesKeys.DAILY_TARGET] ?: 0).coerceIn(0, PracticeStats.DAILY_TARGET_MAX)
    }.share()

    suspend fun setDailyTarget(rounds: Int) {
        context.dataStore.edit { it[PreferencesKeys.DAILY_TARGET] = rounds.coerceIn(0, PracticeStats.DAILY_TARGET_MAX) }
    }

    val reminder: StateFlow<ReminderSettings?> = prefs.map {
        val defaults = ReminderSettings()
        ReminderSettings(
            enabled = it[PreferencesKeys.REMINDER_ENABLED] ?: defaults.enabled,
            minuteOfDay = it[PreferencesKeys.REMINDER_MINUTE]?.takeIf { m -> m in 0 until 24 * 60 } ?: defaults.minuteOfDay
        )
    }.share()

    suspend fun setReminder(settings: ReminderSettings) {
        context.dataStore.edit {
            it[PreferencesKeys.REMINDER_ENABLED] = settings.enabled
            it[PreferencesKeys.REMINDER_MINUTE] = settings.minuteOfDay.coerceIn(0, 24 * 60 - 1)
        }
    }

    /** Whether the user agreed to the accessibility service disclosure. */
    val accessibilityConsent: StateFlow<Boolean?> = prefs.map { it[PreferencesKeys.ACCESSIBILITY_CONSENT] ?: false }.share()

    suspend fun setAccessibilityConsent(agreed: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ACCESSIBILITY_CONSENT] = agreed }
    }

    /** The chosen [InputMode], or null until the user picks one (see [InputMode.default]). */
    val inputModeFlow: Flow<InputMode?> = prefs.map { preferences ->
        InputMode.fromName(preferences[PreferencesKeys.INPUT_MODE])
    }.distinctUntilChanged()
    
    suspend fun setInputMode(mode: InputMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INPUT_MODE] = mode.name
        }
    }
    
    /** Default beads per round for sessions without a goal-specific size. */
    val roundSize: StateFlow<Int?> = prefs.map { preferences ->
        RoundSize.sanitize(preferences[PreferencesKeys.ROUND_SIZE])
    }.share()

    suspend fun setRoundSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ROUND_SIZE] = RoundSize.sanitize(size)
        }
    }

    // Session history. Decoded only when the stored JSON changes, not on every preference write.
    val sessionHistory: StateFlow<List<SessionRecord>?> = prefs.map { it[PreferencesKeys.SESSION_HISTORY] }
        .distinctUntilChanged()
        .map { stored -> json.decodeStoredList(SessionRecord.serializer(), stored).items.withUniqueRecordIds() }
        .share()

    /** Adds [record] at the top, replacing a record with the same ID. History is never truncated. */
    suspend fun addSessionRecord(record: SessionRecord) {
        updateList(PreferencesKeys.SESSION_HISTORY, SessionRecord.serializer()) { it.upsert(record) }
    }

    suspend fun clearSessionHistory() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SESSION_HISTORY] = "[]"
        }
    }

    // Goals
    val goals: StateFlow<List<Goal>?> = prefs.map { it[PreferencesKeys.SAVED_GOALS] }
        .distinctUntilChanged()
        .map { stored -> json.decodeStoredList(Goal.serializer(), stored).items.withUniqueGoalIds() }
        .share()

    /** Replaces the goal with the same ID, or adds it at the top. */
    suspend fun saveGoal(goal: Goal) {
        updateList(PreferencesKeys.SAVED_GOALS, Goal.serializer()) { goals ->
            if (goals.any { it.id == goal.id }) goals.map { if (it.id == goal.id) goal else it } else listOf(goal) + goals
        }
    }

    suspend fun deleteGoal(goalId: String) {
        updateList(PreferencesKeys.SAVED_GOALS, Goal.serializer()) { goals -> goals.filterNot { it.id == goalId } }
    }

    /**
     * Read-modify-write of a stored list that never loses data: elements that don't decode are
     * written back as they were, and an unparseable value is copied to a backup key first.
     */
    private suspend fun <T> updateList(
        key: Preferences.Key<String>,
        serializer: KSerializer<T>,
        transform: (List<T>) -> List<T>
    ) {
        context.dataStore.edit { preferences ->
            val stored = preferences[key]
            val list = json.decodeStoredList(serializer, stored)
            if (list.corrupt && stored != null) {
                Log.e(TAG, "Unreadable ${key.name}; keeping a backup")
                preferences[stringPreferencesKey("${key.name}_backup_${System.currentTimeMillis()}")] = stored
            }
            preferences[key] = json.encodeStoredList(serializer, transform(list.items), list.unreadable)
        }
    }

    /** Everything worth keeping, as a [Backup]. Settings never changed by the user are left out. */
    suspend fun exportBackup(now: Long): Backup {
        val p = prefs.first()
        return Backup(
            exportedAt = now,
            settings = BackupSettings(
                themeMode = when (p[PreferencesKeys.THEME_MODE]) {
                    0 -> ThemeMode.LIGHT.name
                    1 -> ThemeMode.DARK.name
                    2 -> ThemeMode.SYSTEM.name
                    else -> null
                },
                inputMode = p[PreferencesKeys.INPUT_MODE],
                roundSize = p[PreferencesKeys.ROUND_SIZE],
                dailyTarget = p[PreferencesKeys.DAILY_TARGET],
                countVibration = p[PreferencesKeys.COUNT_VIBRATION_ENABLED],
                countIntensity = p[PreferencesKeys.COUNT_VIBRATION_INTENSITY],
                roundVibration = p[PreferencesKeys.VIBRATION_ENABLED],
                roundIntensity = p[PreferencesKeys.ROUND_VIBRATION_INTENSITY],
                milestonePulse = p[PreferencesKeys.MILESTONE_PULSE],
                beadClick = p[PreferencesKeys.BEAD_CLICK],
                pocketGuard = p[PreferencesKeys.POCKET_GUARD],
                reminderEnabled = p[PreferencesKeys.REMINDER_ENABLED],
                reminderMinuteOfDay = p[PreferencesKeys.REMINDER_MINUTE]
            ),
            goals = json.decodeStoredList(Goal.serializer(), p[PreferencesKeys.SAVED_GOALS]).items.withUniqueGoalIds(),
            history = json.decodeStoredList(SessionRecord.serializer(), p[PreferencesKeys.SESSION_HISTORY]).items.withUniqueRecordIds()
        )
    }

    /**
     * Merges [backup] into the stored data in one transaction: sessions and goals are added, or updated
     * when the ID matches, and the backup's settings are applied (invalid values are ignored).
     * Nothing is deleted, so importing twice, or into a phone with newer sessions, is safe.
     */
    suspend fun importBackup(backup: Backup): ImportResult {
        var result = ImportResult(0, 0, 0)
        context.dataStore.edit { p ->
            val history = json.decodeStoredList(SessionRecord.serializer(), p[PreferencesKeys.SESSION_HISTORY])
            val historyIds = history.items.mapTo(HashSet()) { it.id }
            val mergedHistory = BackupCodec.mergeHistory(history.items, backup.history)
            p[PreferencesKeys.SESSION_HISTORY] = json.encodeStoredList(SessionRecord.serializer(), mergedHistory, history.unreadable)

            val goals = json.decodeStoredList(Goal.serializer(), p[PreferencesKeys.SAVED_GOALS])
            val goalIds = goals.items.mapTo(HashSet()) { it.id }
            p[PreferencesKeys.SAVED_GOALS] = json.encodeStoredList(
                Goal.serializer(), BackupCodec.mergeGoals(goals.items, backup.goals), goals.unreadable
            )

            with(backup.settings) {
                when (themeMode) {
                    ThemeMode.LIGHT.name -> p[PreferencesKeys.THEME_MODE] = 0
                    ThemeMode.DARK.name -> p[PreferencesKeys.THEME_MODE] = 1
                    ThemeMode.SYSTEM.name -> p[PreferencesKeys.THEME_MODE] = 2
                }
                InputMode.fromName(inputMode)?.let { p[PreferencesKeys.INPUT_MODE] = it.name }
                roundSize?.takeIf(RoundSize::isValid)?.let { p[PreferencesKeys.ROUND_SIZE] = it }
                dailyTarget?.takeIf { it in 0..PracticeStats.DAILY_TARGET_MAX }?.let { p[PreferencesKeys.DAILY_TARGET] = it }
                countVibration?.let { p[PreferencesKeys.COUNT_VIBRATION_ENABLED] = it }
                countIntensity?.takeIf { it in 1..255 }?.let { p[PreferencesKeys.COUNT_VIBRATION_INTENSITY] = it }
                roundVibration?.let { p[PreferencesKeys.VIBRATION_ENABLED] = it }
                roundIntensity?.takeIf { it in 1..255 }?.let { p[PreferencesKeys.ROUND_VIBRATION_INTENSITY] = it }
                milestonePulse?.let { p[PreferencesKeys.MILESTONE_PULSE] = it }
                beadClick?.let { p[PreferencesKeys.BEAD_CLICK] = it }
                pocketGuard?.let { p[PreferencesKeys.POCKET_GUARD] = it }
                reminderEnabled?.let { p[PreferencesKeys.REMINDER_ENABLED] = it }
                reminderMinuteOfDay?.takeIf { it in 0 until 24 * 60 }?.let { p[PreferencesKeys.REMINDER_MINUTE] = it }
            }

            result = ImportResult(
                sessionsAdded = mergedHistory.count { it.id !in historyIds },
                goalsAdded = backup.goals.count { it.id !in goalIds },
                goalsUpdated = backup.goals.count { it.id in goalIds }
            )
        }
        return result
    }

    // Active session: written on every change while a session runs, cleared when it is saved.
    suspend fun loadActiveSession(): ChantState? = try {
        context.sessionDataStore.data.first()[PreferencesKeys.ACTIVE_SESSION]
            ?.let { json.decodeFromString(ChantState.serializer(), it) }
    } catch (e: Exception) {
        Log.e(TAG, "Reading the active session failed", e)
        null
    }

    suspend fun saveActiveSession(state: ChantState) {
        context.sessionDataStore.edit { it[PreferencesKeys.ACTIVE_SESSION] = json.encodeToString(ChantState.serializer(), state) }
    }

    /** Clears the stored active session only if it is still session [sessionId]. */
    suspend fun clearActiveSession(sessionId: String) {
        context.sessionDataStore.edit { preferences ->
            val stored = preferences[PreferencesKeys.ACTIVE_SESSION] ?: return@edit
            val storedId = runCatching { json.decodeFromString(ChantState.serializer(), stored).sessionId }.getOrNull()
            if (storedId == null || storedId == sessionId) preferences.remove(PreferencesKeys.ACTIVE_SESSION)
        }
    }

    /** Records [state] in history (if anything was counted), then clears it as the active session. */
    fun finishSession(state: ChantState, endTime: Long) = write {
        if (state.count > 0) addSessionRecord(state.toRecord(endTime))
        clearActiveSession(state.sessionId)
    }

    companion object {
        private const val TAG = "PreferencesRepository"

        // Holds only the application context, so this can't leak an Activity.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: PreferencesRepository? = null

        fun get(context: Context): PreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: PreferencesRepository(context.applicationContext).also { instance = it }
            }
    }
}
