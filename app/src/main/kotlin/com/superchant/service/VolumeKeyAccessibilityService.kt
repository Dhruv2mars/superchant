package com.superchant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * AccessibilityService that intercepts volume key presses for chanting counter.
 * 
 * - Captures KEYCODE_VOLUME_UP and KEYCODE_VOLUME_DOWN
 * - Returns true to CONSUME the event (prevents volume change)
 * - Only intercepts when ChantingService is actively running
 */
class VolumeKeyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VolumeKeyA11yService"
        
        const val ACTION_VOLUME_KEY_PRESSED = "com.superchant.action.VOLUME_KEY_PRESSED"
        const val EXTRA_IS_VOLUME_UP = "is_volume_up"

        @Volatile
        private var instance: VolumeKeyAccessibilityService? = null
        
        val isServiceRunning: Boolean
            get() = instance != null

        // SharedFlow for reactive key press handling
        // replay = 1 ensures the latest event is available to new collectors
        // extraBufferCapacity = 64 provides buffer for burst key presses
        private val _volumeKeyEvents = MutableSharedFlow<VolumeKeyEvent>(
            replay = 1,
            extraBufferCapacity = 64
        )
        val volumeKeyEvents: SharedFlow<VolumeKeyEvent> = _volumeKeyEvents.asSharedFlow()

        /**
         * Check if this accessibility service is enabled in system settings.
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val serviceName = "${context.packageName}/${VolumeKeyAccessibilityService::class.java.canonicalName}"
            
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.split(':').any { 
                it.equals(serviceName, ignoreCase = true) 
            }
        }

        /**
         * Open the system Accessibility Settings page.
         */
        fun openAccessibilitySettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }

        /**
         * Check if restricted settings might be blocking accessibility service.
         * On Android 13+, sideloaded apps need "Allow restricted settings" enabled.
         */
        fun needsRestrictedSettingsPermission(context: Context): Boolean {
            // Only applies to Android 13 (TIRAMISU) and above
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return false
            }
            // If accessibility is already enabled, restricted settings isn't blocking
            if (isAccessibilityServiceEnabled(context)) {
                return false
            }
            // Check if app was sideloaded (not from Play Store)
            return try {
                val installerPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstallerPackageName(context.packageName)
                }
                // Sideloaded apps have null installer or are from package installer
                installerPackage == null || 
                    installerPackage == "com.android.packageinstaller" ||
                    installerPackage == "com.google.android.packageinstaller"
            } catch (e: Exception) {
                // If we can't determine, assume it might need it
                true
            }
        }

        /**
         * Open the app's settings page where user can enable "Allow restricted settings"
         */
        fun openAppSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    data class VolumeKeyEvent(
        val isVolumeUp: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service connected")
        
        // Reinforce key event filtering flag
        serviceInfo?.let { info ->
            info.flags = info.flags or 
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.d(TAG, "Accessibility Service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Only process DOWN events to avoid double-counting
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        // Only intercept volume keys
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && 
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        // Check ChantingService state - use currentState directly to avoid race conditions
        // currentState returns null if service instance is not set, which means not running
        val state = ChantingService.currentState
        
        // If state is null, service is not running - don't intercept
        if (state == null) {
            Log.d(TAG, "ChantingService not running, passing volume key through")
            return false
        }
        
        // If session is not active or is paused, don't intercept
        if (!state.isActive || state.isPaused) {
            Log.d(TAG, "Session inactive or paused, passing volume key through")
            return false
        }

        // Session is active and not paused - consume the volume key
        val isUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        handleVolumeKey(isUp)
        return true // CONSUME - prevents volume change
    }

    private fun handleVolumeKey(isUp: Boolean) {
        val keyEvent = VolumeKeyEvent(isVolumeUp = isUp)
        
        // Emit to flow (ChantingService observes this)
        val emitted = _volumeKeyEvents.tryEmit(keyEvent)
        
        if (emitted) {
            Log.d(TAG, "Volume ${if (isUp) "UP" else "DOWN"} pressed and consumed")
        } else {
            // This shouldn't happen with our buffer size, but log if it does
            Log.w(TAG, "Failed to emit volume key event - buffer full or no collectors")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for volume key interception
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
}
