package com.blindtechabbas.darksurvival.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blindtechabbas.darksurvival.GameViewModel
import com.blindtechabbas.darksurvival.ui.screens.MainMenuScreen
import com.blindtechabbas.darksurvival.ui.screens.MatchScreen
import com.blindtechabbas.darksurvival.ui.screens.SettingsScreen

@Composable
fun AppNavHost(viewModel: GameViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main_menu"
    ) {
        composable("main_menu") {
            MainMenuScreen(
                vm = viewModel,
                onStartMatch = { navController.navigate("match") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("match") {
            MatchScreen(
                vm = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                vm = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
