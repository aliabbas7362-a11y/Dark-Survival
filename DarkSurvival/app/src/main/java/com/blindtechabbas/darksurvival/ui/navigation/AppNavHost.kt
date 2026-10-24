package com.blindtechabbas.darksurvival.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechabbas.darksurvival.game.GameViewModel
import com.blindtechabbas.darksurvival.game.Screen
import com.blindtechabbas.darksurvival.ui.screens.GateScreen
import com.blindtechabbas.darksurvival.ui.screens.MainMenuScreen
import com.blindtechabbas.darksurvival.ui.screens.MatchScreen
import com.blindtechabbas.darksurvival.ui.screens.ResultScreen
import com.blindtechabbas.darksurvival.ui.screens.SettingsScreen

/**
 * Screen switching. Back never leaves by accident: it asks for a second press
 * (the view model speaks the question) and the game closes only after it says goodbye.
 */
@Composable
fun AppNavHost(viewModel: GameViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(enabled = true) { viewModel.onBack() }

    LaunchedEffect(ui.exit) {
        if (ui.exit) {
            (context as? Activity)?.finish()
        }
    }

    when (ui.screen) {
        Screen.GATE -> GateScreen(viewModel)
        Screen.MAIN_MENU -> MainMenuScreen(viewModel)
        Screen.SETTINGS -> SettingsScreen(viewModel)
        Screen.MATCH -> MatchScreen(viewModel)
        Screen.RESULT -> ResultScreen(viewModel)
    }
}
