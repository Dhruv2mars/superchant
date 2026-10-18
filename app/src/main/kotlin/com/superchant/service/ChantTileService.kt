package com.superchant.service

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.superchant.AppShortcuts
import com.superchant.R
import com.superchant.data.ChantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile. Idle, it opens the app and starts counting; during a session it shows the count
 * and pauses or resumes, which helps volume-button users who chant with the screen off.
 */
class ChantTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch { ChantingService.chantStateFlow.collect(::render) }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val state = ChantingService.chantStateFlow.value
        if (ChantingService.isRunning && state.isActive && !state.isGoalComplete) {
            ChantingService.setPaused(!state.isPaused)
        } else if (isLocked) {
            unlockAndRun(::openApp)
        } else {
            openApp()
        }
    }

    private fun openApp() {
        val intent = AppShortcuts.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(state: ChantState) {
        val tile = qsTile ?: return
        val running = ChantingService.isRunning && state.isActive
        tile.label = getString(R.string.app_name)
        tile.state = if (running && !state.isPaused) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val subtitle = when {
            !running -> getString(R.string.tile_start)
            state.isGoalComplete -> getString(R.string.notification_goal_complete)
            state.isPaused -> getString(R.string.status_paused)
            else -> "${state.currentRoundCount} / ${ChantState.ROUND_SIZE}"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        tile.contentDescription = "${getString(R.string.app_name)}, $subtitle"
        tile.updateTile()
    }
}
