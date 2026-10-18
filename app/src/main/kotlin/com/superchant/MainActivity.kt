package com.superchant

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.superchant.data.PreferencesRepository
import com.superchant.reminder.ReminderScheduler
import com.superchant.service.ChantingService
import com.superchant.ui.screens.GoalsScreen
import com.superchant.ui.screens.HistoryScreen
import com.superchant.ui.screens.HomeScreen
import com.superchant.ui.screens.SettingsScreen
import com.superchant.ui.screens.StartRequest
import com.superchant.ui.theme.SuperChantTheme
import com.superchant.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        DebugStrictMode.enable()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val preferencesRepository = PreferencesRepository.get(this)
        // Not on recreation (e.g. rotation) or a relaunch from Recents, which re-delivers the old intent
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) launchRequest.value = AppShortcuts.parse(intent)
        
        // Hold the first frame (the system splash stays up) until the saved theme is known,
        // so a forced light/dark theme never flashes the system one.
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val ready = preferencesRepository.themeMode.value != null
                if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                return ready
            }
        })
        
        setContent {
            val themeMode = preferencesRepository.themeMode.collectAsStateWithLifecycle().value ?: ThemeMode.SYSTEM
            
            SuperChantTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    // A start from the Goals screen, a shortcut or the tile; Home runs it after its permission checks
                    var pendingStart by remember { mutableStateOf<StartRequest?>(null) }
                    val launchStart by launchRequest
                    LaunchedEffect(launchStart) {
                        val request = launchStart ?: return@LaunchedEffect
                        AppShortcuts.reportUsed(this@MainActivity, request)
                        val goals = preferencesRepository.goals.filterNotNull().first()
                        // A shortcut to a deleted goal falls back to a free session
                        val goal = request.goalId?.let { id -> goals.firstOrNull { it.id == id } }
                        pendingStart = StartRequest(goal)
                        // Cleared only now: clearing it earlier changes this effect's key and cancels it
                        // while the goals are still loading (a cold start from the tile or a shortcut)
                        launchRequest.value = null
                        navController.popBackStack("home", inclusive = false)
                    }
                    val goals by preferencesRepository.goals.collectAsStateWithLifecycle()
                    LaunchedEffect(goals) { goals?.let { AppShortcuts.updateGoalShortcuts(applicationContext, it) } }
                    
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = {
                            fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(300)
                            )
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(300)
                            )
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        composable("home") {
                            HomeScreen(
                                themeMode = themeMode,
                                preferencesRepository = preferencesRepository,
                                pendingStart = pendingStart,
                                onPendingStartHandled = { pendingStart = null },
                                onSettingsClick = { navController.navigate("settings") },
                                onHistoryClick = { navController.navigate("history") },
                                onGoalsClick = { navController.navigate("goals") }
                            )
                        }
                        
                        composable("settings") {
                            SettingsScreen(
                                preferencesRepository = preferencesRepository,
                                themeMode = themeMode,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        
                        composable("history") {
                            HistoryScreen(
                                preferencesRepository = preferencesRepository,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        
                        composable("goals") {
                            GoalsScreen(
                                preferencesRepository = preferencesRepository,
                                onBackClick = { navController.popBackStack() },
                                onStartGoal = { goal ->
                                    pendingStart = StartRequest(goal)
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    /** A start asked for by the intent that opened the app (launcher shortcut, Quick Settings tile). */
    private val launchRequest = mutableStateOf<AppShortcuts.Request?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppShortcuts.parse(intent)?.let { launchRequest.value = it }
    }

    override fun onStart() {
        super.onStart()
        // Re-arm the daily reminder in case the alarm was lost (e.g. the app was force-stopped).
        lifecycleScope.launch(Dispatchers.Default) { ReminderScheduler.sync(applicationContext) }
        // A session persisted by a process that died (and wasn't restarted yet) resumes here.
        lifecycleScope.launch {
            if (!ChantingService.isRunning && PreferencesRepository.get(this@MainActivity).loadActiveSession() != null) {
                ChantingService.restore(this@MainActivity)
            }
        }
    }
}
