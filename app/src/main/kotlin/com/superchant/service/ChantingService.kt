package com.superchant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.superchant.MainActivity
import com.superchant.R
import com.superchant.data.ChantCounter
import com.superchant.data.ChantState
import com.superchant.data.CountFeedback
import com.superchant.data.Goal
import com.superchant.data.InputMode
import com.superchant.data.PreferencesRepository
import com.superchant.data.RestoreAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Foreground service that manages the chanting session.
 *
 * Counts volume presses from VolumeKeyAccessibilityService and on-screen taps (via [countFromScreen])
 * through one increment path ([ChantCounter]), and:
 * - Increments the count (or undoes the last one)
 * - Triggers vibration feedback
 * - Updates the notification
 * - Persists the active session on every change, so it is restored after process death
 *   (START_STICKY restart, or the next app launch) and saved to history exactly once.
 */
class ChantingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var notificationManager: NotificationManager
    private lateinit var vibrator: Vibrator
    private lateinit var preferencesRepository: PreferencesRepository

    // Set from the start intent (or the setting on restore), then kept in sync with the setting
    // so mid-session changes apply
    private var inputMode: InputMode = InputMode.VOLUME

    override fun onCreate() {
        super.onCreate()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        preferencesRepository = PreferencesRepository.get(this)

        createNotificationChannel()
        serviceScope.launch { preferencesRepository.inputModeFlow.collect { mode -> if (mode != null) inputMode = mode } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        InputMode.fromName(intent?.getStringExtra(EXTRA_INPUT_MODE))?.let { inputMode = it }
        when (intent?.action) {
            ACTION_START -> startSession(intent.getStringExtra(EXTRA_GOAL)?.let { goalJson ->
                runCatching { Json.decodeFromString(Goal.serializer(), goalJson) }.getOrNull()
            })
            ACTION_STOP -> stopSession()
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            // null: restarted by the system after process death (START_STICKY)
            else -> restoreSession()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        // Normally stopSession() already reset the state. If the service is destroyed mid-session,
        // the persisted copy stays and is restored on the next app launch.
        if (_instance === this) {
            _instance = null
            _sharedChantState.value = ChantState()
        }
        super.onDestroy()
    }

    private val state: ChantState
        get() = _sharedChantState.value

    /** Publishes [newState] and persists it while the session is active. */
    private fun setState(newState: ChantState) {
        _sharedChantState.value = newState
        if (newState.isActive) preferencesRepository.write { saveActiveSession(newState) }
    }

    private fun startSession(goal: Goal?) {
        val now = System.currentTimeMillis()
        // Starting over a running session (e.g. a goal from the Goals screen) saves it first.
        val previous = state
        if (previous.isActive && previous.count > 0) {
            preferencesRepository.write { addSessionRecord(previous.toRecord(now)) }
        }

        setState(ChantState.start(goal, now))
        _instance = this
        if (!enterForeground()) abandonSession()
    }

    private fun restoreSession() {
        if (_instance === this && state.isActive) {
            updateNotification()
            return
        }
        serviceScope.launch {
            val saved = preferencesRepository.loadActiveSession()
            inputMode = preferencesRepository.inputModeFlow.first()
                ?: InputMode.default(VolumeKeyAccessibilityService.isAccessibilityServiceEnabled(this@ChantingService))
            // A session may have been started while loading.
            if (state.isActive) return@launch
            when (val action = saved?.restoreAction(System.currentTimeMillis())) {
                is RestoreAction.Resume -> {
                    _sharedChantState.value = action.state
                    _instance = this@ChantingService
                    if (!enterForeground()) {
                        // Not allowed from the background here; keep the saved copy for the next launch.
                        _instance = null
                        _sharedChantState.value = ChantState()
                        stopSelf()
                    }
                }
                is RestoreAction.Finish -> {
                    preferencesRepository.write { addSessionRecord(action.record) }
                    preferencesRepository.write { clearActiveSession(saved.sessionId) }
                    stopSelf()
                }
                RestoreAction.Discard -> {
                    preferencesRepository.write { clearActiveSession(saved.sessionId) }
                    stopSelf()
                }
                null -> stopSelf()
            }
        }
    }

    private fun stopSession() {
        val current = state
        _instance = null
        _sharedChantState.value = ChantState()

        if (current.isActive) {
            preferencesRepository.finishSession(current, System.currentTimeMillis())
        } else {
            // A fresh process got STOP before restoring: save whatever was persisted.
            preferencesRepository.write {
                loadActiveSession()?.takeIf { it.isActive }?.let { finishSession(it, maxOf(it.lastUpdatedAt, it.sessionStartTime)) }
            }
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Drops a session that could not be shown as a foreground service. */
    private fun abandonSession() {
        val current = state
        _instance = null
        _sharedChantState.value = ChantState()
        preferencesRepository.finishSession(current, System.currentTimeMillis())
        stopSelf()
    }

    private fun enterForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        lastNotifiedAt = SystemClock.uptimeMillis()
        true
    } catch (e: Exception) {
        // e.g. ForegroundServiceStartNotAllowedException when restarted in the background
        Log.w(TAG, "Could not enter the foreground", e)
        false
    }

    private fun setPaused(paused: Boolean) {
        if (!state.isActive) return
        setState(state.copy(isPaused = paused, lastUpdatedAt = System.currentTimeMillis()))
        updateNotification()
    }

    /** The single increment path for every input source. Returns false if nothing was counted. */
    private fun incrementCount(): Boolean {
        val result = ChantCounter.increment(state) ?: return false
        setState(result.state.copy(lastUpdatedAt = System.currentTimeMillis()))
        updateNotification()
        giveFeedback(result.feedback, result.state.rounds)
        return true
    }

    private fun undoCount() {
        val undone = ChantCounter.undo(state) ?: return
        setState(undone.copy(lastUpdatedAt = System.currentTimeMillis()))
        updateNotification()
        val settings = preferencesRepository.vibrationSettings.value
        if (settings?.countEnabled == true) vibrateUndo(settings.countIntensity)
    }

    /** Two quick ticks, distinct from the single count tick. */
    private fun vibrateUndo(amplitude: Int) {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 20, 70, 20), intArrayOf(0, amplitude, 0, amplitude), -1))
    }

    private fun giveFeedback(feedback: CountFeedback, rounds: Int) {
        // Feedback waits for the stored settings rather than guessing defaults.
        val settings = preferencesRepository.vibrationSettings.value
        when (feedback) {
            CountFeedback.GOAL -> {
                playSound(RingtoneManager.TYPE_RINGTONE)
                if (settings?.roundEnabled == true) vibrateGoalComplete()
            }
            CountFeedback.SECTION -> {
                playSound(RingtoneManager.TYPE_NOTIFICATION)
                if (settings?.roundEnabled == true) vibrateRoundComplete(1, settings.roundIntensity)
            }
            CountFeedback.ROUND -> if (settings?.roundEnabled == true) vibrateRoundComplete(rounds, settings.roundIntensity)
            CountFeedback.COUNT -> if (settings?.countEnabled == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, settings.countIntensity))
            }
        }
    }

    /** Vibrates once per completed round (capped at 10): 150ms on, 100ms off. */
    private fun vibrateRoundComplete(rounds: Int, intensity: Int) {
        val pulses = rounds.coerceIn(1, 10)
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        repeat(pulses) {
            timings += listOf(0L, 150L)
            amplitudes += listOf(0, intensity)
            if (it < pulses - 1) {
                timings += 100L
                amplitudes += 0
            }
        }
        vibrator.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1))
    }

    private fun vibrateGoalComplete() {
        // Celebration pattern: long-short-short-long
        val timings = longArrayOf(0, 300, 100, 150, 100, 150, 100, 300)
        val amplitudes = intArrayOf(0, 255, 0, 200, 0, 200, 0, 255)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Plays the default sound of [type], falling back to the notification sound. Failures are ignored. */
    private fun playSound(type: Int) {
        try {
            val uri = RingtoneManager.getDefaultUri(type)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, uri)?.play()
        } catch (e: Exception) {
            // Silently fail if sound can't be played
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT // Higher importance for faster updates
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
            // Disable lights and sounds to avoid annoyance while keeping faster updates
            enableLights(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    // Cached builder; actions are only rebuilt when the paused state changes.
    private var cachedNotificationBuilder: NotificationCompat.Builder? = null
    private var actionsPaused: Boolean? = null

    private fun buildNotification(): Notification {
        val state = state

        val builder = cachedNotificationBuilder ?: NotificationCompat.Builder(this, CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_notification)
            setOngoing(true)
            setSilent(true)
            setOnlyAlertOnce(true)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setCategory(NotificationCompat.CATEGORY_PROGRESS)
            setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

            // Content intent - open app
            val contentIntent = PendingIntent.getActivity(
                this@ChantingService,
                REQUEST_CODE_CONTENT,
                Intent(this@ChantingService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PENDING_INTENT_FLAGS
            )
            setContentIntent(contentIntent)
        }.also { cachedNotificationBuilder = it }

        val title = when {
            state.isPaused -> getString(R.string.notification_paused)
            state.isGoalComplete -> getString(R.string.notification_goal_complete)
            else -> "${state.currentRoundCount} / ${ChantState.COUNTS_PER_ROUND}"
        }

        val goal = state.activeGoal
        val currentMantra = state.currentMantra
        val text = when {
            goal != null && state.isGoalComplete ->
                resources.getQuantityString(R.plurals.notification_goal_complete_text, goal.totalRounds, goal.name, goal.totalRounds)
            goal != null && currentMantra != null ->
                "${currentMantra.name} (${state.mantraRoundsCompleted}/${currentMantra.targetRounds})"
            goal != null -> resources.getQuantityString(R.plurals.rounds_count, state.rounds, state.rounds)
            else -> resources.getQuantityString(R.plurals.rounds_completed, state.rounds, state.rounds)
        }

        builder.setContentTitle(title)
        builder.setContentText(text)
        builder.setProgress(ChantState.COUNTS_PER_ROUND, state.currentRoundCount, false)

        if (actionsPaused != state.isPaused) {
            actionsPaused = state.isPaused
            builder.clearActions()
            if (state.isPaused) {
                builder.addServiceAction(R.string.action_resume, REQUEST_CODE_PAUSE_RESUME, ACTION_RESUME)
            } else {
                builder.addServiceAction(R.string.action_pause, REQUEST_CODE_PAUSE_RESUME, ACTION_PAUSE)
            }
            builder.addServiceAction(R.string.action_stop, REQUEST_CODE_STOP, ACTION_STOP)
        }

        return builder.build()
    }

    private fun NotificationCompat.Builder.addServiceAction(titleRes: Int, requestCode: Int, action: String) {
        val intent = PendingIntent.getService(this@ChantingService, requestCode, intent(this@ChantingService, action), PENDING_INTENT_FLAGS)
        addAction(R.drawable.ic_notification, getString(titleRes), intent)
    }

    private var lastNotifiedAt = 0L
    private var pendingNotification: Job? = null

    /**
     * Posts the current state, at most every [NOTIFICATION_INTERVAL_MS]; faster changes are
     * coalesced into one trailing update (the system drops updates above ~5/s anyway).
     */
    private fun updateNotification() {
        if (pendingNotification?.isActive == true) return
        val wait = NOTIFICATION_INTERVAL_MS - (SystemClock.uptimeMillis() - lastNotifiedAt)
        pendingNotification = serviceScope.launch {
            if (wait > 0) delay(wait)
            if (_instance === this@ChantingService) {
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                lastNotifiedAt = SystemClock.uptimeMillis()
            }
        }
    }

    companion object {
        private const val TAG = "ChantingService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "chanting_session"
        private const val NOTIFICATION_INTERVAL_MS = 250L

        // PendingIntent request codes
        private const val REQUEST_CODE_CONTENT = 100
        private const val REQUEST_CODE_STOP = 101
        private const val REQUEST_CODE_PAUSE_RESUME = 102
        private const val PENDING_INTENT_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        private const val ACTION_START = "com.superchant.action.START"
        private const val ACTION_STOP = "com.superchant.action.STOP"
        private const val ACTION_PAUSE = "com.superchant.action.PAUSE"
        private const val ACTION_RESUME = "com.superchant.action.RESUME"
        private const val ACTION_RESTORE = "com.superchant.action.RESTORE"
        private const val EXTRA_GOAL = "com.superchant.extra.GOAL"
        private const val EXTRA_INPUT_MODE = "com.superchant.extra.INPUT_MODE"

        @Volatile
        private var _instance: ChantingService? = null

        // Shared state flow that persists across service lifecycle
        // This ensures UI collectors always get updates from the same flow instance
        private val _sharedChantState = MutableStateFlow(ChantState())
        val chantStateFlow: StateFlow<ChantState> = _sharedChantState.asStateFlow()

        val isRunning: Boolean
            get() = _instance != null

        /**
         * Called by VolumeKeyAccessibilityService on the main thread for each new volume press.
         * Returns true if the press was counted, in which case the key should be consumed.
         */
        fun onVolumeKeyPressed(): Boolean =
            acceptsVolumeKeys && _instance?.incrementCount() == true

        /** Whether the running session counts volume keys; false in on-screen-only mode so they change volume. */
        val acceptsVolumeKeys: Boolean
            get() = _instance?.inputMode?.usesVolumeKeys == true

        /** Counts one on-screen tap through the same path as a volume press. Call on the main thread. */
        fun countFromScreen() {
            _instance?.takeIf { it.inputMode.usesScreen }?.incrementCount()
        }

        /** Removes the last count, if any. Call on the main thread. */
        fun undo() {
            _instance?.undoCount()
        }

        private fun intent(context: Context, action: String) =
            Intent(context, ChantingService::class.java).setAction(action)

        private fun startIntent(context: Context, inputMode: InputMode) =
            intent(context, ACTION_START).putExtra(EXTRA_INPUT_MODE, inputMode.name)

        fun start(context: Context, inputMode: InputMode) {
            context.startForegroundService(startIntent(context, inputMode))
        }

        fun stop(context: Context) {
            context.startService(intent(context, ACTION_STOP))
        }

        fun startWithGoal(context: Context, goal: Goal, inputMode: InputMode) {
            context.startForegroundService(
                startIntent(context, inputMode).putExtra(EXTRA_GOAL, Json.encodeToString(Goal.serializer(), goal))
            )
        }

        /** Resumes a session persisted by a process that has died; call while the app is visible. */
        fun restore(context: Context) {
            context.startService(intent(context, ACTION_RESTORE))
        }
    }
}
