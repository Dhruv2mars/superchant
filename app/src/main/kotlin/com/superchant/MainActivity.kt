package com.superchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.superchant.data.PreferencesRepository
import com.superchant.service.ChantingService
import com.superchant.ui.screens.GoalsScreen
import com.superchant.ui.screens.HistoryScreen
import com.superchant.ui.screens.HomeScreen
import com.superchant.ui.screens.SettingsScreen
import com.superchant.ui.theme.SuperChantTheme
import com.superchant.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val preferencesRepository = PreferencesRepository(applicationContext)
        
        setContent {
            val themeMode by preferencesRepository.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            
            SuperChantTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    
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
                                    ChantingService.startWithGoal(applicationContext, goal)
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
