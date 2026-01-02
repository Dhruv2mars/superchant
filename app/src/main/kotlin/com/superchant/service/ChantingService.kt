package com.superchant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
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
import com.superchant.data.ChantState
import com.superchant.data.Goal
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
 * Listens to volume key events from VolumeKeyAccessibilityService and:
 * - Increments the count
 * - Triggers vibration feedback
 * - Updates the notification
 * - Persists session data
 */
class ChantingService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Job for volume key collection - allows proper cancellation
    private var volumeKeyCollectionJob: Job? = null
    
    // Jobs for preference collection
    private var vibrationPrefsJob: Job? = null
    
    private lateinit var notificationManager: NotificationManager
    private lateinit var vibrator: Vibrator
    private lateinit var preferencesRepository: PreferencesRepository
    
    // Cached vibration preferences for fast access during counting
    @Volatile private var cachedVibrationEnabled: Boolean = true
    @Volatile private var cachedCountVibrationEnabled: Boolean = true
    @Volatile private var cachedCountVibrationIntensity: Int = 80
    @Volatile private var cachedRoundVibrationIntensity: Int = 255
    
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
        vibrationPrefsJob?.cancel()
        vibrationPrefsJob = serviceScope.launch {
            // Collect all preferences in parallel
            launch {
                preferencesRepository.vibrationEnabledFlow.collect { enabled ->
                    cachedVibrationEnabled = enabled
                }
            }
            launch {
                preferencesRepository.countVibrationEnabledFlow.collect { enabled ->
                    cachedCountVibrationEnabled = enabled
                }
            }
            launch {
                preferencesRepository.countVibrationIntensityFlow.collect { intensity ->
                    cachedCountVibrationIntensity = intensity
                }
            }
            launch {
                preferencesRepository.roundVibrationIntensityFlow.collect { intensity ->
                    cachedRoundVibrationIntensity = intensity
                }
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        vibrationPrefsJob?.cancel()
        vibrationPrefsJob = null
        
        // Save current session before destruction if there's unsaved data
        // This handles cases where the service is killed by the system
        val currentState = _sharedChantState.value
        if (currentState.isActive && currentState.count > 0) {
            runBlocking {
                val record = SessionRecord(
                    startTime = currentState.sessionStartTime,
                    endTime = System.currentTimeMillis(),
                    totalCounts = currentState.count,
                    totalRounds = currentState.rounds
                )
                preferencesRepository.addSessionRecord(record)
            }
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
            count = 0,
            rounds = 0,
            isActive = true,
            isPaused = false,
            sessionStartTime = System.currentTimeMillis(),
            activeGoal = goal,
            currentMantraIndex = 0,
            mantraRoundsCompleted = 0
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
        
        // Save session to history SYNCHRONOUSLY if there were any counts
        // Using runBlocking ensures the session is saved before the service stops
        if (currentState.count > 0) {
            runBlocking {
                val record = SessionRecord(
                    startTime = currentState.sessionStartTime,
                    endTime = System.currentTimeMillis(),
                    totalCounts = currentState.count,
                    totalRounds = currentState.rounds,
                    goalId = currentState.activeGoal?.id,
                    goalName = currentState.activeGoal?.name
                )
                preferencesRepository.addSessionRecord(record)
            }
        }
        
        // Stop foreground service
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun pauseSession() {
        _sharedChantState.value = _sharedChantState.value.copy(isPaused = true)
        updateNotification()
    }
    
    private fun resumeSession() {
        _sharedChantState.value = _sharedChantState.value.copy(isPaused = false)
        updateNotification()
    }
    
    private fun incrementCount() {
        val currentState = _sharedChantState.value
        if (!currentState.isActive || currentState.isPaused) return
        
        // Check if goal is already complete - don't allow more counting
        if (currentState.isGoalComplete) return
        
        val newCount = currentState.count + 1
        val newRounds = newCount / ChantState.COUNTS_PER_ROUND
        val previousRounds = currentState.rounds
        
        // Goal-based tracking
        val goal = currentState.activeGoal
        var newMantraIndex = currentState.currentMantraIndex
        var newMantraRoundsCompleted = currentState.mantraRoundsCompleted
        var sectionCompleted = false
        var goalCompleted = false
        
        if (goal != null && newRounds > previousRounds) {
            // A round was completed
            newMantraRoundsCompleted++
            
            val currentMantra = goal.mantras.getOrNull(newMantraIndex)
            if (currentMantra != null && newMantraRoundsCompleted >= currentMantra.targetRounds) {
                // Current mantra section is complete
                if (newMantraIndex < goal.mantras.size - 1) {
                    // Move to next mantra
                    sectionCompleted = true
                    newMantraIndex++
                    newMantraRoundsCompleted = 0
                } else {
                    // This was the last mantra - goal complete!
                    goalCompleted = true
                }
            }
        }
        
        // Update state immediately for fastest UI response
        _sharedChantState.value = currentState.copy(
            count = newCount,
            rounds = newRounds,
            currentMantraIndex = newMantraIndex,
            mantraRoundsCompleted = newMantraRoundsCompleted
        )
        
        // Update notification IMMEDIATELY on main thread (no coroutine delay)
        // This ensures lock screen and notification panel update as fast as possible
        updateNotification()
        
        // Handle sounds and vibrations - count and round vibrations are independent
        if (goalCompleted) {
            // Goal complete! Play celebration sound and vibration
            playGoalCompleteSound()
            vibrateGoalComplete()
        } else if (sectionCompleted) {
            // Section complete - play notification sound
            playSectionCompleteSound()
            if (cachedVibrationEnabled) {
                vibrateRoundComplete(1)
            }
        } else if (newRounds > previousRounds) {
            // Regular round completed - vibrate if enabled
            if (cachedVibrationEnabled) {
                vibrateRoundComplete(newRounds)
            }
        } else if (cachedCountVibrationEnabled) {
            // Regular count - soft quick vibration (independent of round vibration)
            vibrateCount()
        }
    }
    
    private fun vibrateCount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(30, cachedCountVibrationIntensity)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }
    
    private fun vibrateRoundComplete(rounds: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create a pattern: vibrate N times with pauses
            // Each vibration: 150ms on, 100ms off
            val timings = mutableListOf<Long>()
            val amplitudes = mutableListOf<Int>()
            
            repeat(rounds.coerceAtMost(10)) { // Cap at 10 to avoid very long vibration
                timings.add(0) // delay before vibration
                timings.add(150) // vibration duration
                amplitudes.add(0) // no vibration during delay
                amplitudes.add(cachedRoundVibrationIntensity)
                if (it < rounds - 1) {
                    timings.add(100) // pause between vibrations
                    amplitudes.add(0)
                }
            }
            
            val effect = VibrationEffect.createWaveform(
                timings.toLongArray(),
                amplitudes.toIntArray(),
                -1 // don't repeat
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val pattern = LongArray(rounds * 2) { i ->
                if (i % 2 == 0) 100L else 150L
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
    
    private fun vibrateGoalComplete() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Special celebration pattern: long-short-long-short-long
            val timings = longArrayOf(0, 300, 100, 150, 100, 150, 100, 300)
            val amplitudes = intArrayOf(0, 255, 0, 200, 0, 200, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 300, 100, 150, 100, 150, 100, 300), -1)
        }
    }
    
    private fun playSectionCompleteSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone?.play()
        } catch (e: Exception) {
            // Silently fail if sound can't be played
        }
    }
    
    private fun playGoalCompleteSound() {
        try {
            // Play a more celebratory sound for goal completion
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone?.play()
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
        val currentRoundCount = state.count % ChantState.COUNTS_PER_ROUND
        
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
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setContentIntent(contentIntent)
        }.also { cachedNotificationBuilder = it }
        
        // Update dynamic content
        val title = if (state.isPaused) {
            "Paused"
        } else if (state.isGoalComplete) {
            "Goal Complete!"
        } else {
            "${currentRoundCount} / ${ChantState.COUNTS_PER_ROUND}"
        }
        
        val text = if (state.activeGoal != null) {
            val goal = state.activeGoal
            val currentMantra = goal.mantras.getOrNull(state.currentMantraIndex)
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
        builder.setProgress(ChantState.COUNTS_PER_ROUND, currentRoundCount, false)
        
        // Update actions (need to clear and re-add as pause/resume state changes)
        builder.clearActions()
        
        // Pause/Resume action
        val pauseResumeIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_PAUSE_RESUME,
            Intent(this, ChantingService::class.java).apply {
                action = if (state.isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(
            R.drawable.ic_notification,
            if (state.isPaused) getString(R.string.action_resume) else getString(R.string.action_pause),
            pauseResumeIntent
        )
        
        // Stop action
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            Intent(this, ChantingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(
            R.drawable.ic_notification,
            getString(R.string.action_stop),
            stopIntent
        )
        
        return builder.build()
    }
    
    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }
    
    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "chanting_session"
        
        // PendingIntent request codes
        private const val REQUEST_CODE_CONTENT = 100
        private const val REQUEST_CODE_STOP = 101
        private const val REQUEST_CODE_PAUSE_RESUME = 102
        
        const val ACTION_START = "com.superchant.action.START"
        const val ACTION_START_WITH_GOAL = "com.superchant.action.START_WITH_GOAL"
        const val ACTION_STOP = "com.superchant.action.STOP"
        const val ACTION_PAUSE = "com.superchant.action.PAUSE"
        const val ACTION_RESUME = "com.superchant.action.RESUME"
        
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
        
        fun start(context: Context) {
            val intent = Intent(context, ChantingService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, ChantingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
        
        fun pause(context: Context) {
            val intent = Intent(context, ChantingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }
        
        fun resume(context: Context) {
            val intent = Intent(context, ChantingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }
        
        fun startWithGoal(context: Context, goal: Goal) {
            _pendingGoalForStart = goal
            val intent = Intent(context, ChantingService::class.java).apply {
                action = ACTION_START_WITH_GOAL
            }
            context.startForegroundService(intent)
        }
    }
}
