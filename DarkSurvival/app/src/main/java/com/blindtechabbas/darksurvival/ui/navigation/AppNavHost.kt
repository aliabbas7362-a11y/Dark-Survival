package com.blindtechabbas.darksurvival.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechabbas.darksurvival.game.GameViewModel
import com.blindtechabbas.darksurvival.game.Screen
import com.blindtechabbas.darksurvival.ui.screens.ChapterSelectScreen
import com.blindtechabbas.darksurvival.ui.screens.MainMenuScreen
import com.blindtechabbas.darksurvival.ui.screens.MatchScreen
import com.blindtechabbas.darksurvival.ui.screens.SettingsScreen

@Composable
fun AppNavHost(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // ---- BACK BUTTON = in-app panel exit, NEVER the phone home screen ----
    // Back pops the current panel to its parent screen inside the app:
    // Settings -> Main Menu, Chapter Select -> Main Menu, Match -> Main Menu
    // (results included). Only on Main Menu does back leave the app.
    when (state.screen) {
        is Screen.MainMenu -> BackHandler {}
        is Screen.ChapterSelect -> BackHandler { viewModel.goToMainMenu() }
        is Screen.Match -> BackHandler { viewModel.goToMainMenu() }
        is Screen.Settings -> BackHandler { viewModel.goToMainMenu() }
    }

    when (val s = state.screen) {
        is Screen.MainMenu -> MainMenuScreen(viewModel)
        is Screen.ChapterSelect -> ChapterSelectScreen(viewModel)
        is Screen.Match -> MatchScreen(viewModel)
        is Screen.Settings -> SettingsScreen(viewModel)
    }
}
