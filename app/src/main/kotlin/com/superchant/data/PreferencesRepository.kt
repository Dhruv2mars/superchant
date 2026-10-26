package com.superchant.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.superchant.ui.theme.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
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
    /** A distinct pulse every [ChantState.MILESTONE_INTERVAL] beads. */
    val milestonePulse: Boolean = true,
    /** A soft synthesized bead click on each count; plays through the system sounds volume. */
    val beadClick: Boolean = false
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
class PreferencesRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val sessionDataStore: DataStore<Preferences>
) {

    private object PreferencesKeys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val COUNT_VIBRATION_ENABLED = booleanPreferencesKey("count_vibration_enabled")
        val COUNT_VIBRATION_INTENSITY = intPreferencesKey("count_vibration_intensity")
        val ROUND_VIBRATION_INTENSITY = intPreferencesKey("round_vibration_intensity")
        val SESSION_HISTORY = stringPreferencesKey("session_history")
        val SAVED_GOALS = stringPreferencesKey("saved_goals")
        val ACTIVE_SESSION = stringPreferencesKey("active_session")
        // Keys of removed features ("input_mode", "round_size", "pocket_guard") may still be stored; they are never read.
        val MILESTONE_PULSE = booleanPreferencesKey("milestone_pulse")
        val BEAD_CLICK = booleanPreferencesKey("bead_click")
        val ACCESSIBILITY_CONSENT = booleanPreferencesKey("accessibility_consent")
        val DAILY_TARGET = intPreferencesKey("daily_target")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_HANDLED_THROUGH = longPreferencesKey("reminder_handled_through")
        val TOUCH_HINT_SEEN = booleanPreferencesKey("touch_hint_seen")
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

    /**
     * Runs [block] after every write queued so far (e.g. to read the active session after a Stop has
     * saved it) and returns its result. Never call it from inside a [write] block: it would wait for itself.
     */
    suspend fun <T> afterWrites(block: suspend PreferencesRepository.() -> T): T {
        val result = CompletableDeferred<T>()
        write { result.completeWith(runCatching { block() }) }
        return result.await()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // A read error (e.g. IOException) shows defaults instead of crashing every collector, and reading is
    // retried, so the stored values (and every later change) still arrive once the store is readable again.
    private val prefs: Flow<Preferences> = dataStore.data.retryWhen { e, attempt ->
        if (e !is IOException) return@retryWhen false
        Log.e(TAG, "Reading preferences failed", e)
        if (attempt == 0L) emit(emptyPreferences())
        delay(minOf(attempt + 1, 30) * 1000)
        true
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
        dataStore.edit { preferences ->
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
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun setCountVibrationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COUNT_VIBRATION_ENABLED] = enabled
        }
    }

    // Vibration intensity settings (1-255)
    suspend fun setCountVibrationIntensity(intensity: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COUNT_VIBRATION_INTENSITY] = intensity.coerceIn(1, 255)
        }
    }

    suspend fun setRoundVibrationIntensity(intensity: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ROUND_VIBRATION_INTENSITY] = intensity.coerceIn(1, 255)
        }
    }

    val countingFeel: StateFlow<CountingFeel?> = prefs.map { preferences ->
        val defaults = CountingFeel()
        CountingFeel(
            milestonePulse = preferences[PreferencesKeys.MILESTONE_PULSE] ?: defaults.milestonePulse,
            beadClick = preferences[PreferencesKeys.BEAD_CLICK] ?: defaults.beadClick
        )
    }.share()

    suspend fun setMilestonePulse(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.MILESTONE_PULSE] = enabled }
    }

    suspend fun setBeadClick(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.BEAD_CLICK] = enabled }
    }

    /** Rounds per day to aim for; 0 means no target. */
    val dailyTarget: StateFlow<Int?> = prefs.map {
        (it[PreferencesKeys.DAILY_TARGET] ?: 0).coerceIn(0, PracticeStats.DAILY_TARGET_MAX)
    }.share()

    suspend fun setDailyTarget(rounds: Int) {
        dataStore.edit { it[PreferencesKeys.DAILY_TARGET] = rounds.coerceIn(0, PracticeStats.DAILY_TARGET_MAX) }
    }

    private fun Preferences.reminderSettings(): ReminderSettings {
        val defaults = ReminderSettings()
        return ReminderSettings(
            enabled = this[PreferencesKeys.REMINDER_ENABLED] ?: defaults.enabled,
            minuteOfDay = this[PreferencesKeys.REMINDER_MINUTE]?.takeIf { m -> m in 0 until 24 * 60 } ?: defaults.minuteOfDay
        )
    }

    val reminder: StateFlow<ReminderSettings?> = prefs.map { it.reminderSettings() }.share()

    suspend fun setReminder(settings: ReminderSettings) {
        dataStore.edit {
            it[PreferencesKeys.REMINDER_ENABLED] = settings.enabled
            it[PreferencesKeys.REMINDER_MINUTE] = settings.minuteOfDay.coerceIn(0, 24 * 60 - 1)
            // A changed reminder starts from now: one due earlier today isn't owed anymore
            it[PreferencesKeys.REMINDER_HANDLED_THROUGH] = System.currentTimeMillis()
        }
    }

    /** Marks reminders due up to [time] as handled (the alarm fired). */
    suspend fun setReminderHandledThrough(time: Long) {
        dataStore.edit { it[PreferencesKeys.REMINDER_HANDLED_THROUGH] = time }
    }

    /**
     * The reminder settings as stored right now (the [reminder] flow can lag just after a write), and the
     * time up to which reminders were handled (0 if never).
     */
    suspend fun loadReminder(): Pair<ReminderSettings, Long> =
        prefs.first().let { it.reminderSettings() to (it[PreferencesKeys.REMINDER_HANDLED_THROUGH] ?: 0L) }

    /** Whether the user agreed to the accessibility service disclosure. */
    val accessibilityConsent: StateFlow<Boolean?> = prefs.map { it[PreferencesKeys.ACCESSIBILITY_CONSENT] ?: false }.share()

    suspend fun setAccessibilityConsent(agreed: Boolean) {
        dataStore.edit { it[PreferencesKeys.ACCESSIBILITY_CONSENT] = agreed }
    }

    /** Whether the one-time hint for the touch-counting toggle has been shown. */
    val touchHintSeen: StateFlow<Boolean?> = prefs.map { it[PreferencesKeys.TOUCH_HINT_SEEN] ?: false }.share()

    suspend fun setTouchHintSeen() {
        dataStore.edit { it[PreferencesKeys.TOUCH_HINT_SEEN] = true }
    }

    // Session history. Decoded only when the stored JSON changes, not on every preference write.
    val sessionHistory: StateFlow<List<SessionRecord>?> = prefs.map { it[PreferencesKeys.SESSION_HISTORY] }
        .distinctUntilChanged()
        .map { stored -> json.decodeStoredList(SessionRecord.serializer(), stored).items.withUniqueRecordIds() }
        .share()

    /** Adds [record] at the top, replacing a record with the same ID. History is never truncated. */
    suspend fun addSessionRecord(record: SessionRecord) {
        updateList(PreferencesKeys.SESSION_HISTORY, SessionRecord.serializer(), { withUniqueRecordIds() }) { it.upsert(record) }
    }

    suspend fun clearSessionHistory() {
        dataStore.edit { preferences ->
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
        updateList(PreferencesKeys.SAVED_GOALS, Goal.serializer(), { withUniqueGoalIds() }) { goals ->
            if (goals.any { it.id == goal.id }) goals.map { if (it.id == goal.id) goal else it } else listOf(goal) + goals
        }
    }

    suspend fun deleteGoal(goalId: String) {
        updateList(PreferencesKeys.SAVED_GOALS, Goal.serializer(), { withUniqueGoalIds() }) { goals -> goals.filterNot { it.id == goalId } }
    }

    /** Read-modify-write of a stored list (see [readListForUpdate]). */
    private suspend fun <T> updateList(
        key: Preferences.Key<String>,
        serializer: KSerializer<T>,
        uniqueIds: List<T>.() -> List<T>,
        transform: (List<T>) -> List<T>
    ) {
        dataStore.edit { preferences ->
            val list = preferences.readListForUpdate(key, serializer, uniqueIds)
            preferences[key] = json.encodeStoredList(serializer, transform(list.items), list.unreadable)
        }
    }

    /**
     * Reads a stored list to change it without losing data: elements that don't decode are kept to be
     * written back as they were, an unparseable value is copied to a backup key first, and duplicate IDs
     * get the same replacements the screens show ([uniqueIds]), so a change hits the item that was shown.
     */
    private fun <T> MutablePreferences.readListForUpdate(
        key: Preferences.Key<String>,
        serializer: KSerializer<T>,
        uniqueIds: List<T>.() -> List<T>
    ): StoredList<T> {
        val stored = this[key]
        val list = json.decodeStoredList(serializer, stored)
        if (list.corrupt && stored != null) {
            Log.e(TAG, "Unreadable ${key.name}; keeping a backup")
            this[stringPreferencesKey("${key.name}_backup_${System.currentTimeMillis()}")] = stored
        }
        return StoredList(list.items.uniqueIds(), list.unreadable)
    }

    /**
     * Everything worth keeping, as a [Backup]. Settings never changed by the user are left out. Reads the
     * store directly, so a read error fails the export instead of writing a backup of the defaults.
     */
    suspend fun exportBackup(now: Long): Backup {
        val p = dataStore.data.first()
        return Backup(
            exportedAt = now,
            settings = BackupSettings(
                themeMode = when (p[PreferencesKeys.THEME_MODE]) {
                    0 -> ThemeMode.LIGHT.name
                    1 -> ThemeMode.DARK.name
                    2 -> ThemeMode.SYSTEM.name
                    else -> null
                },
                dailyTarget = p[PreferencesKeys.DAILY_TARGET],
                countVibration = p[PreferencesKeys.COUNT_VIBRATION_ENABLED],
                countIntensity = p[PreferencesKeys.COUNT_VIBRATION_INTENSITY],
                roundVibration = p[PreferencesKeys.VIBRATION_ENABLED],
                roundIntensity = p[PreferencesKeys.ROUND_VIBRATION_INTENSITY],
                milestonePulse = p[PreferencesKeys.MILESTONE_PULSE],
                beadClick = p[PreferencesKeys.BEAD_CLICK],
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
        dataStore.edit { p ->
            val history = p.readListForUpdate(PreferencesKeys.SESSION_HISTORY, SessionRecord.serializer()) { withUniqueRecordIds() }
            val historyIds = history.items.mapTo(HashSet()) { it.id }
            val mergedHistory = BackupCodec.mergeHistory(history.items, backup.history)
            p[PreferencesKeys.SESSION_HISTORY] = json.encodeStoredList(SessionRecord.serializer(), mergedHistory, history.unreadable)

            val goals = p.readListForUpdate(PreferencesKeys.SAVED_GOALS, Goal.serializer()) { withUniqueGoalIds() }
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
                dailyTarget?.takeIf { it in 0..PracticeStats.DAILY_TARGET_MAX }?.let { p[PreferencesKeys.DAILY_TARGET] = it }
                countVibration?.let { p[PreferencesKeys.COUNT_VIBRATION_ENABLED] = it }
                countIntensity?.takeIf { it in 1..255 }?.let { p[PreferencesKeys.COUNT_VIBRATION_INTENSITY] = it }
                roundVibration?.let { p[PreferencesKeys.VIBRATION_ENABLED] = it }
                roundIntensity?.takeIf { it in 1..255 }?.let { p[PreferencesKeys.ROUND_VIBRATION_INTENSITY] = it }
                milestonePulse?.let { p[PreferencesKeys.MILESTONE_PULSE] = it }
                beadClick?.let { p[PreferencesKeys.BEAD_CLICK] = it }
                reminderEnabled?.let { p[PreferencesKeys.REMINDER_ENABLED] = it }
                reminderMinuteOfDay?.takeIf { it in 0 until 24 * 60 }?.let { p[PreferencesKeys.REMINDER_MINUTE] = it }
                // Like a change in Settings, an imported reminder starts from now
                if (reminderEnabled != null || reminderMinuteOfDay != null) {
                    p[PreferencesKeys.REMINDER_HANDLED_THROUGH] = System.currentTimeMillis()
                }
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
        sessionDataStore.data.first()[PreferencesKeys.ACTIVE_SESSION]
            ?.let { json.decodeFromString(ChantState.serializer(), it) }
            // Older versions allowed other round sizes; rounds and goal progress follow the count at 108 now
            ?.normalizedRounds()
    } catch (e: Exception) {
        Log.e(TAG, "Reading the active session failed", e)
        null
    }

    suspend fun saveActiveSession(state: ChantState) {
        sessionDataStore.edit { it[PreferencesKeys.ACTIVE_SESSION] = json.encodeToString(ChantState.serializer(), state) }
    }

    /** Clears the stored active session only if it is still session [sessionId]. */
    suspend fun clearActiveSession(sessionId: String) {
        sessionDataStore.edit { preferences ->
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

        @Volatile
        private var instance: PreferencesRepository? = null

        fun get(context: Context): PreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: context.applicationContext.let { PreferencesRepository(it.dataStore, it.sessionDataStore) }
                    .also { instance = it }
            }
    }
}
