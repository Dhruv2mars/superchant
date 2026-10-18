package com.superchant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.net.toUri

/**
 * AccessibilityService that intercepts volume key presses for chanting counter.
 * 
 * - Captures KEYCODE_VOLUME_UP and KEYCODE_VOLUME_DOWN
 * - Consumes a press (DOWN, repeats and UP) only when ChantingService counted it
 * - Passes keys through when no session is running, it is paused, its goal is complete, or the
 *   session counts on-screen only ([ChantingService.acceptsVolumeKeys])
 */
class VolumeKeyAccessibilityService : AccessibilityService() {

    companion object {
        /**
         * Check if this accessibility service is enabled in system settings.
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val service = ComponentName(context, VolumeKeyAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            // Entries may use the full or the short ("pkg/.Class") form.
            return enabledServices.split(':').any { ComponentName.unflattenFromString(it) == service }
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
                    data = "package:${context.packageName}".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    private val keyFilter = VolumeKeyFilter(ChantingService::onVolumeKeyPressed)

    override fun onServiceConnected() {
        super.onServiceConnected()
        // When the system rebinds the service after the app's process died, it does not restore
        // key filtering from the XML config; setting the flag again re-registers the key filter.
        serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS }
    }

    // Runs on the main thread, like ChantingService, so counting happens synchronously here.
    override fun onKeyEvent(event: KeyEvent): Boolean =
        keyFilter.onKey(event.keyCode, event.action, event.repeatCount)

    // Only key events are used; the config subscribes to no accessibility events.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
