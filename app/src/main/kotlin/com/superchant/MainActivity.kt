package com.superchant

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
import com.superchant.data.Goal
import com.superchant.data.PreferencesRepository
import com.superchant.reminder.ReminderScheduler
import com.superchant.service.ChantingService
import com.superchant.ui.screens.GoalsScreen
import com.superchant.ui.screens.HistoryScreen
import com.superchant.ui.screens.HomeScreen
import com.superchant.ui.screens.SettingsScreen
import com.superchant.ui.theme.SuperChantTheme
import com.superchant.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val preferencesRepository = PreferencesRepository.get(this)
        
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
                    // Goal picked on the Goals screen; Home starts it after its permission checks
                    var pendingGoal by remember { mutableStateOf<Goal?>(null) }
                    
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
                                pendingGoal = pendingGoal,
                                onPendingGoalHandled = { pendingGoal = null },
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
                                    pendingGoal = goal
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Re-arm the daily reminder in case the alarm was lost (e.g. the app was force-stopped).
        lifecycleScope.launch { ReminderScheduler.sync(applicationContext) }
        // A session persisted by a process that died (and wasn't restarted yet) resumes here.
        lifecycleScope.launch {
            if (!ChantingService.isRunning && PreferencesRepository.get(this@MainActivity).loadActiveSession() != null) {
                ChantingService.restore(this@MainActivity)
            }
        }
    }
}
