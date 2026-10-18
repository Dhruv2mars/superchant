package com.superchant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import com.superchant.data.SessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground service that manages the chanting session.
 * 
 * Counts volume key events from VolumeKeyAccessibilityService and on-screen taps (via [countFromScreen])
 * through one increment path, and:
 * - Increments the count (or undoes the last one)
 * - Triggers vibration feedback
 * - Updates the notification
 * - Persists session data
 */
class ChantingService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Job for volume key collection - allows proper cancellation
    private var volumeKeyCollectionJob: Job? = null
    
    private lateinit var notificationManager: NotificationManager
    private lateinit var vibrator: Vibrator
    private lateinit var preferencesRepository: PreferencesRepository
    
    // Cached vibration preferences for fast access during counting
    @Volatile private var cachedVibrationEnabled: Boolean = true
    @Volatile private var cachedCountVibrationEnabled: Boolean = true
    @Volatile private var cachedCountVibrationIntensity: Int = 80
    @Volatile private var cachedRoundVibrationIntensity: Int = 255
    
    // Set from the start intent, then kept in sync with the setting so mid-session changes apply
    @Volatile private var inputMode: InputMode = InputMode.VOLUME
    
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
        preferencesRepository = PreferencesRepository(this)
        
        createNotificationChannel()
        
        // Start collecting vibration preferences to cache them
        startVibrationPrefsCollection()
    }
    
    private fun startVibrationPrefsCollection() {
        with(preferencesRepository) {
            serviceScope.launch { vibrationEnabledFlow.collect { cachedVibrationEnabled = it } }
            serviceScope.launch { countVibrationEnabledFlow.collect { cachedCountVibrationEnabled = it } }
            serviceScope.launch { countVibrationIntensityFlow.collect { cachedCountVibrationIntensity = it } }
            serviceScope.launch { roundVibrationIntensityFlow.collect { cachedRoundVibrationIntensity = it } }
            serviceScope.launch { inputModeFlow.collect { mode -> if (mode != null) inputMode = mode } }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        InputMode.fromName(intent?.getStringExtra(EXTRA_INPUT_MODE))?.let { inputMode = it }
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_START_WITH_GOAL -> {
                // Get goal from static pending goal (set via startWithGoal companion function)
                val goal = _pendingGoalForStart
                _pendingGoalForStart = null
                startSession(goal)
            }
            ACTION_STOP -> stopSession()
            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            null -> {
                // Service was restarted by the system (START_STICKY behavior)
                // Check if we had an active session that needs to be restored
                val currentState = _sharedChantState.value
                if (currentState.isActive) {
                    // Restore the session - re-register instance and restart collection
                    _instance = this
                    startVolumeKeyCollection()
                    startForeground(NOTIFICATION_ID, buildNotification())
                } else {
                    // No active session, stop the service
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        // Cancel collection jobs first to prevent any more processing
        volumeKeyCollectionJob?.cancel()
        volumeKeyCollectionJob = null
        
        // Save current session before destruction if there's unsaved data
        val currentState = _sharedChantState.value
        if (currentState.isActive && currentState.count > 0) {
            saveSession(currentState, goal = null)
        }
        
        // Reset the shared state ONLY if we're actually stopping (not just being killed)
        // If isActive is true, the system killed us and we might restart via START_STICKY
        // In that case, keep the state so we can restore
        // If isActive is false, this is a clean shutdown via stopSession()
        if (!currentState.isActive) {
            _sharedChantState.value = ChantState()
        }
        
        serviceScope.cancel()
        _instance = null
        super.onDestroy()
    }
    
    private fun startSession(goal: Goal? = null) {
        // CRITICAL: Set _instance FIRST so VolumeKeyAccessibilityService.isRunning
        // returns true immediately. This prevents a race condition where volume
        // presses during startup aren't consumed.
        _instance = this
        
        _sharedChantState.value = ChantState(
            isActive = true,
            sessionStartTime = System.currentTimeMillis(),
            activeGoal = goal
        )
        
        // Start collecting volume key events from accessibility service
        startVolumeKeyCollection()
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, buildNotification())
    }
    
    private fun startVolumeKeyCollection() {
        // Cancel any existing collection to prevent duplicate collectors
        volumeKeyCollectionJob?.cancel()
        
        // Record the start time to filter out stale events from replay buffer
        val collectionStartTime = System.currentTimeMillis()
        
        volumeKeyCollectionJob = serviceScope.launch {
            VolumeKeyAccessibilityService.volumeKeyEvents.collect { keyEvent ->
                // Filter out stale events from replay buffer
                // Only process events that occurred after we started collecting
                if (keyEvent.timestamp < collectionStartTime) {
                    return@collect // Skip stale replayed event
                }
                
                // The AccessibilityService already checks if we're running/paused
                // but double-check here for safety
                val currentState = _sharedChantState.value
                if (currentState.isActive && !currentState.isPaused) {
                    incrementCount()
                }
            }
        }
    }
    
    private fun stopSession() {
        val currentState = _sharedChantState.value
        
        // Cancel volume key collection first
        volumeKeyCollectionJob?.cancel()
        volumeKeyCollectionJob = null
        
        // Reset state to inactive BEFORE clearing _instance
        // This ensures proper synchronization with VolumeKeyAccessibilityService
        _sharedChantState.value = ChantState()
        
        // Clear singleton AFTER state is reset
        _instance = null
        
        if (currentState.count > 0) {
            saveSession(currentState, currentState.activeGoal)
        }
        
        // Stop foreground service
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /** Saves synchronously so the record is written before the service stops. */
    private fun saveSession(state: ChantState, goal: Goal?) = runBlocking {
        preferencesRepository.addSessionRecord(
            SessionRecord(
                startTime = state.sessionStartTime,
                endTime = System.currentTimeMillis(),
                totalCounts = state.count,
                totalRounds = state.rounds,
                goalId = goal?.id,
                goalName = goal?.name
            )
        )
    }
    
    private fun pauseSession() {
        _sharedChantState.value = _sharedChantState.value.copy(isPaused = true)
        updateNotification()
    }
    
    private fun resumeSession() {
        _sharedChantState.value = _sharedChantState.value.copy(isPaused = false)
        updateNotification()
    }
    
    /** The single increment path for every input source. */
    private fun incrementCount() {
        val result = ChantCounter.increment(_sharedChantState.value) ?: return
        
        // Update state immediately for fastest UI response
        _sharedChantState.value = result.state
        
        // Update notification IMMEDIATELY on main thread (no coroutine delay)
        // This ensures lock screen and notification panel update as fast as possible
        updateNotification()
        
        // Handle sounds and vibrations - count and round vibrations are independent
        when (result.feedback) {
            CountFeedback.GOAL -> {
                playSound(RingtoneManager.TYPE_RINGTONE)
                vibrateGoalComplete()
            }
            CountFeedback.SECTION -> {
                playSound(RingtoneManager.TYPE_NOTIFICATION)
                if (cachedVibrationEnabled) vibrateRoundComplete(1)
            }
            CountFeedback.ROUND -> if (cachedVibrationEnabled) vibrateRoundComplete(result.state.rounds)
            CountFeedback.COUNT -> if (cachedCountVibrationEnabled) vibrateCount()
        }
    }
    
    private fun undoCount() {
        _sharedChantState.value = ChantCounter.undo(_sharedChantState.value) ?: return
        updateNotification()
        if (cachedCountVibrationEnabled) vibrateUndo()
    }
    
    private fun vibrateCount() {
        vibrator.vibrate(VibrationEffect.createOneShot(30, cachedCountVibrationIntensity))
    }
    
    /** Two quick ticks, distinct from the single count tick. */
    private fun vibrateUndo() {
        val amplitude = cachedCountVibrationIntensity
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 20, 70, 20), intArrayOf(0, amplitude, 0, amplitude), -1))
    }
    
    /** Vibrates once per completed round (capped at 10): 150ms on, 100ms off. */
    private fun vibrateRoundComplete(rounds: Int) {
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        repeat(rounds.coerceAtMost(10)) {
            timings += listOf(0L, 150L)
            amplitudes += listOf(0, cachedRoundVibrationIntensity)
            if (it < rounds - 1) {
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
    
    // Cache notification builder to avoid recreating it every time
    private var cachedNotificationBuilder: NotificationCompat.Builder? = null
    
    private fun buildNotification(): Notification {
        val state = _sharedChantState.value
        
        // Reuse builder if possible for faster updates
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
        
        // Update dynamic content
        val title = if (state.isPaused) {
            "Paused"
        } else if (state.isGoalComplete) {
            "Goal Complete!"
        } else {
            "${state.currentRoundCount} / ${ChantState.COUNTS_PER_ROUND}"
        }
        
        val text = if (state.activeGoal != null) {
            val goal = state.activeGoal
            val currentMantra = state.currentMantra
            if (state.isGoalComplete) {
                "${goal.name} - All ${goal.totalRounds} rounds completed"
            } else if (currentMantra != null) {
                "${currentMantra.name} (${state.mantraRoundsCompleted}/${currentMantra.targetRounds})"
            } else {
                "${state.rounds} rounds"
            }
        } else if (state.rounds == 1) {
            "${state.rounds} round completed"
        } else {
            "${state.rounds} rounds completed"
        }
        
        builder.setContentTitle(title)
        builder.setContentText(text)
        builder.setProgress(ChantState.COUNTS_PER_ROUND, state.currentRoundCount, false)
        
        // Update actions (need to clear and re-add as pause/resume state changes)
        builder.clearActions()
        
        if (state.isPaused) {
            builder.addServiceAction(R.string.action_resume, REQUEST_CODE_PAUSE_RESUME, ACTION_RESUME)
        } else {
            builder.addServiceAction(R.string.action_pause, REQUEST_CODE_PAUSE_RESUME, ACTION_PAUSE)
        }
        builder.addServiceAction(R.string.action_stop, REQUEST_CODE_STOP, ACTION_STOP)
        
        return builder.build()
    }
    
    private fun NotificationCompat.Builder.addServiceAction(titleRes: Int, requestCode: Int, action: String) {
        val intent = PendingIntent.getService(this@ChantingService, requestCode, intent(this@ChantingService, action), PENDING_INTENT_FLAGS)
        addAction(R.drawable.ic_notification, getString(titleRes), intent)
    }
    
    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "chanting_session"
        
        // PendingIntent request codes
        private const val REQUEST_CODE_CONTENT = 100
        private const val REQUEST_CODE_STOP = 101
        private const val REQUEST_CODE_PAUSE_RESUME = 102
        private const val PENDING_INTENT_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        
        private const val ACTION_START = "com.superchant.action.START"
        private const val ACTION_START_WITH_GOAL = "com.superchant.action.START_WITH_GOAL"
        private const val ACTION_STOP = "com.superchant.action.STOP"
        private const val ACTION_PAUSE = "com.superchant.action.PAUSE"
        private const val ACTION_RESUME = "com.superchant.action.RESUME"
        private const val EXTRA_INPUT_MODE = "com.superchant.extra.INPUT_MODE"
        
        @Volatile
        private var _instance: ChantingService? = null
        
        // Pending goal for startWithGoal - stored statically since we can't pass complex objects via Intent
        @Volatile
        private var _pendingGoalForStart: Goal? = null
        
        // Shared state flow that persists across service lifecycle
        // This ensures UI collectors always get updates from the same flow instance
        private val _sharedChantState = MutableStateFlow(ChantState())
        val chantStateFlow: StateFlow<ChantState> = _sharedChantState.asStateFlow()
        
        val isRunning: Boolean
            get() = _instance != null
        
        /**
         * Get the current chant state (for AccessibilityService to check pause state)
         */
        val currentState: ChantState?
            get() = if (_instance != null) _sharedChantState.value else null
        
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
        
        private fun startIntent(context: Context, action: String, inputMode: InputMode) =
            intent(context, action).putExtra(EXTRA_INPUT_MODE, inputMode.name)
        
        fun start(context: Context, inputMode: InputMode) {
            context.startForegroundService(startIntent(context, ACTION_START, inputMode))
        }
        
        fun stop(context: Context) {
            context.startService(intent(context, ACTION_STOP))
        }
        
        fun startWithGoal(context: Context, goal: Goal, inputMode: InputMode) {
            _pendingGoalForStart = goal
            context.startForegroundService(startIntent(context, ACTION_START_WITH_GOAL, inputMode))
        }
    }
}
