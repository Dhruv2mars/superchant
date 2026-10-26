package com.superchant.bridge

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.superchant.AppShortcuts
import com.superchant.data.Backup
import com.superchant.data.BackupCodec
import com.superchant.data.BackupFormatException
import com.superchant.data.ChantState
import com.superchant.data.Goal
import com.superchant.data.PreferencesRepository
import com.superchant.data.ReminderSettings
import com.superchant.data.RestoreAction
import com.superchant.data.SessionRecord
import com.superchant.data.ThemeMode
import com.superchant.reminder.ReminderScheduler
import com.superchant.reminder.ReminderTime
import com.superchant.service.ChantingService
import com.superchant.service.VolumeKeyAccessibilityService
import expo.modules.kotlin.Promise
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The JavaScript face of the Kotlin engine. Everything that has to work without JavaScript (counting volume
 * keys, the foreground service and its notification, the tile, reminders, restoring a session after process
 * death) stays in Kotlin; JS gets coarse session snapshots (never one per count), the stored settings, goals
 * and history, and calls the same entry points the Compose screens used.
 */
class SuperchantNativeModule : Module() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var pendingFile: Pair<Int, Promise>? = null
    private var pendingBackupText: String? = null

    private val context: Context
        get() = appContext.reactContext?.applicationContext ?: throw CodedException("ERR_NO_CONTEXT", "React context lost", null)

    private val repository: PreferencesRepository
        get() = PreferencesRepository.get(context)

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun sessionJson(state: ChantState) = json.encodeToString(ChantState.serializer(), state)

    override fun definition() = ModuleDefinition {
        Name("SuperchantNative")

        Events("onSession", "onSettings", "onHistory", "onGoals", "onStartRequest")

        OnCreate {
            val repo = repository
            val appContext = context
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).apply {
                // Only coarse changes reach JS; the count itself is drawn by CounterView
                launch {
                    ChantingService.chantStateFlow
                        .distinctUntilChanged { a, b -> a.copy(count = 0, lastUpdatedAt = 0) == b.copy(count = 0, lastUpdatedAt = 0) }
                        .collect { sendEvent("onSession", mapOf("state" to sessionJson(it))) }
                }
                launch { settingsFlow(repo).collect { sendEvent("onSettings", it) } }
                launch {
                    repo.sessionHistory.filterNotNull().collect {
                        sendEvent("onHistory", mapOf("json" to json.encodeToString(ListSerializer(SessionRecord.serializer()), it)))
                    }
                }
                launch {
                    repo.goals.filterNotNull().collect { goals ->
                        AppShortcuts.updateGoalShortcuts(appContext, goals)
                        sendEvent("onGoals", mapOf("json" to json.encodeToString(ListSerializer(Goal.serializer()), goals)))
                    }
                }
            }
        }

        OnDestroy {
            scope?.cancel()
            scope = null
        }

        // MainActivity.onStart in the Kotlin app: re-arm the reminder and resume a session whose process died.
        OnActivityEntersForeground {
            val ctx = context
            scope?.launch {
                ReminderScheduler.sync(ctx)
                val saved = PreferencesRepository.get(ctx).afterWrites { loadActiveSession() }
                if (saved != null && !ChantingService.isRunning) ChantingService.restore(ctx)
            }
        }

        OnNewIntent { intent ->
            AppShortcuts.parse(intent)?.let { sendEvent("onStartRequest", mapOf("goalId" to it.goalId)) }
        }

        OnActivityResult { _, payload ->
            val (requestCode, promise) = pendingFile ?: return@OnActivityResult
            if (payload.requestCode != requestCode) return@OnActivityResult
            pendingFile = null
            val uri = payload.data?.data
            if (payload.resultCode != Activity.RESULT_OK || uri == null) {
                promise.resolve(null)
                return@OnActivityResult
            }
            val ctx = context
            val text = pendingBackupText
            pendingBackupText = null
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (requestCode == REQUEST_CREATE) {
                        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text!!.toByteArray()) }
                        promise.resolve(true)
                    } else {
                        // Backups are small; refuse anything absurd rather than running out of memory
                        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readNBytesCompat(MAX_BACKUP_BYTES + 1) }
                        if (bytes.size > MAX_BACKUP_BYTES) {
                            promise.reject(CodedException("ERR_BACKUP_FORMAT", "Too large", null))
                        } else {
                            promise.resolve(bytes.decodeToString())
                        }
                    }
                } catch (e: Exception) {
                    promise.reject(CodedException("ERR_BACKUP_FILE", e.message, e))
                }
            }
        }

        // --- Session -------------------------------------------------------------------------------------

        Function("getSession") { sessionJson(ChantingService.chantStateFlow.value) }

        Function("start") { goalJson: String?, touchCounting: Boolean ->
            val goal = goalJson?.let { json.decodeFromString(Goal.serializer(), it) }
            val ctx = context
            onMain { ChantingService.start(ctx, goal, touchCounting) }
        }

        Function("stop") {
            val ctx = context
            onMain { ChantingService.stop(ctx) }
        }

        Function("restore") {
            val ctx = context
            onMain { ChantingService.restore(ctx) }
        }

        Function("setPaused") { paused: Boolean -> onMain { ChantingService.setPaused(paused) } }
        Function("setTouchCounting") { on: Boolean -> onMain { ChantingService.setTouchCounting(on) } }
        Function("countFromScreen") { onMain { ChantingService.countFromScreen() } }

        /** A session persisted by a process that died that would be resumed, not ended (HomeScreen's pendingStart rule). */
        AsyncFunction("hasResumableSession") Coroutine { ->
            repository.afterWrites { loadActiveSession()?.restoreAction(System.currentTimeMillis()) is RestoreAction.Resume }
        }

        // --- Launch requests (launcher shortcuts, Quick Settings tile) -------------------------------------

        /** The START_COUNTING request that launched the activity, once; not on a relaunch from Recents. */
        Function("takeLaunchRequest") {
            val intent = appContext.currentActivity?.intent ?: return@Function null
            val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
            val request = AppShortcuts.parse(intent)?.takeUnless { fromHistory } ?: return@Function null
            intent.action = Intent.ACTION_MAIN
            mapOf("goalId" to request.goalId)
        }

        Function("reportShortcutUsed") { goalId: String? ->
            AppShortcuts.reportUsed(context, AppShortcuts.Request(goalId))
        }

        // --- Stored data -----------------------------------------------------------------------------------

        AsyncFunction("getSettings") Coroutine { -> settingsFlow(repository).first() }

        AsyncFunction("getHistory") Coroutine { ->
            json.encodeToString(ListSerializer(SessionRecord.serializer()), repository.sessionHistory.filterNotNull().first())
        }

        AsyncFunction("getGoals") Coroutine { ->
            json.encodeToString(ListSerializer(Goal.serializer()), repository.goals.filterNotNull().first())
        }

        Function("setThemeMode") { mode: String -> repository.write { setThemeMode(ThemeMode.valueOf(mode)) } }
        Function("setRoundVibration") { on: Boolean -> repository.write { setVibrationEnabled(on) } }
        Function("setCountVibration") { on: Boolean -> repository.write { setCountVibrationEnabled(on) } }
        Function("setCountIntensity") { v: Int -> repository.write { setCountVibrationIntensity(v) } }
        Function("setRoundIntensity") { v: Int -> repository.write { setRoundVibrationIntensity(v) } }
        Function("setMilestonePulse") { on: Boolean -> repository.write { setMilestonePulse(on) } }
        Function("setBeadClick") { on: Boolean -> repository.write { setBeadClick(on) } }
        Function("setDailyTarget") { v: Int -> repository.write { setDailyTarget(v) } }
        Function("setAccessibilityConsent") { on: Boolean -> repository.write { setAccessibilityConsent(on) } }
        Function("setTouchHintSeen") { repository.write { setTouchHintSeen() } }

        /** Saves the reminder setting, then matches the alarm to it (ReminderControls in the Kotlin app). */
        AsyncFunction("setReminder") Coroutine { enabled: Boolean, minuteOfDay: Int ->
            val repo = repository
            repo.afterWrites { setReminder(ReminderSettings(enabled, minuteOfDay)) }
            ReminderScheduler.sync(context)
        }

        Function("saveGoal") { goalJson: String ->
            val goal = json.decodeFromString(Goal.serializer(), goalJson)
            repository.write { saveGoal(goal) }
        }
        Function("deleteGoal") { goalId: String -> repository.write { deleteGoal(goalId) } }
        Function("clearHistory") { repository.write { clearSessionHistory() } }

        /** Every write queued so far has finished. */
        AsyncFunction("flushWrites") Coroutine { -> repository.afterWrites { } }

        AsyncFunction("exportBackup") Coroutine { now: Double ->
            val repo = repository
            repo.afterWrites { BackupCodec.encode(exportBackup(now.toLong())) }
        }

        /** Decodes and merges a backup file's text; rejects with ERR_BACKUP_FORMAT for files that aren't SuperChant backups. */
        AsyncFunction("importBackup") Coroutine { text: String ->
            val backup: Backup = try {
                withContext(Dispatchers.Default) { BackupCodec.decode(text) }
            } catch (e: BackupFormatException) {
                throw CodedException("ERR_BACKUP_FORMAT", e.message, e)
            }
            val repo = repository
            val ctx = context
            val result = repo.afterWrites {
                importBackup(backup).also {
                    // The backup may turn the reminder on; without the notification permission here it could never
                    // show, so it stays off until turned on in Settings (which asks for it). BackupControls.kt.
                    if (backup.settings.reminderEnabled == true && !canPostNotifications(ctx)) {
                        setReminder(ReminderSettings(enabled = false, minuteOfDay = backup.settings.reminderMinuteOfDay ?: ReminderTime.DEFAULT_MINUTE_OF_DAY))
                    }
                }
            }
            ReminderScheduler.sync(ctx)
            mapOf("sessionsAdded" to result.sessionsAdded, "goalsAdded" to result.goalsAdded, "goalsUpdated" to result.goalsUpdated)
        }

        /** Storage Access Framework "save as" (CreateDocument); resolves true when written, null when cancelled. */
        AsyncFunction("saveBackupFile") { fileName: String, text: String, promise: Promise ->
            val activity = appContext.currentActivity ?: throw CodedException("ERR_NO_ACTIVITY", "No activity", null)
            pendingFile?.second?.resolve(null)
            pendingFile = REQUEST_CREATE to promise
            pendingBackupText = text
            activity.startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(Backup.MIME_TYPE)
                    .putExtra(Intent.EXTRA_TITLE, fileName),
                REQUEST_CREATE
            )
        }.runOnQueue(Queues.MAIN)

        /** Storage Access Framework open (OpenDocument); resolves the file's text, or null when cancelled. */
        AsyncFunction("openBackupFile") { promise: Promise ->
            val activity = appContext.currentActivity ?: throw CodedException("ERR_NO_ACTIVITY", "No activity", null)
            pendingFile?.second?.resolve(null)
            pendingFile = REQUEST_OPEN to promise
            activity.startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    // Some file managers label JSON as text or octet-stream
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(Backup.MIME_TYPE, "text/plain", "application/octet-stream")),
                REQUEST_OPEN
            )
        }.runOnQueue(Queues.MAIN)

        // --- Volume buttons and permissions ----------------------------------------------------------------

        Function("isAccessibilityServiceEnabled") { VolumeKeyAccessibilityService.isAccessibilityServiceEnabled(context) }
        Function("openAccessibilitySettings") { VolumeKeyAccessibilityService.openAccessibilitySettings(context) }
        Function("needsRestrictedSettings") { VolumeKeyAccessibilityService.needsRestrictedSettingsPermission(context) }
        Function("openAppSettings") { VolumeKeyAccessibilityService.openAppSettings(context) }

        Function("notificationsGranted") { canPostNotifications(context) }

        /** Whether the vibrator can vary its strength (the intensity sliders only show where it can). */
        Function("hasAmplitudeControl") {
            val ctx = context
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.hasAmplitudeControl()
        }

        View(CounterView::class)
    }

    private fun settingsFlow(repo: PreferencesRepository) = combine(
        combine(repo.themeMode.filterNotNull(), repo.vibrationSettings.filterNotNull(), repo.countingFeel.filterNotNull(), ::Triple),
        combine(repo.dailyTarget.filterNotNull(), repo.reminder.filterNotNull(), repo.accessibilityConsent.filterNotNull(), ::Triple),
        repo.touchHintSeen.filterNotNull()
    ) { (theme, vibration, feel), (target, reminder, consent), hintSeen ->
        mapOf(
            "themeMode" to theme.name,
            "roundVibration" to vibration.roundEnabled,
            "countVibration" to vibration.countEnabled,
            "countIntensity" to vibration.countIntensity,
            "roundIntensity" to vibration.roundIntensity,
            "milestonePulse" to feel.milestonePulse,
            "beadClick" to feel.beadClick,
            "dailyTarget" to target,
            "reminderEnabled" to reminder.enabled,
            "reminderMinuteOfDay" to reminder.minuteOfDay,
            "accessibilityConsent" to consent,
            "touchHintSeen" to hintSeen
        )
    }.distinctUntilChanged()

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val REQUEST_CREATE = 4201
        const val REQUEST_OPEN = 4202
        const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    }
}
