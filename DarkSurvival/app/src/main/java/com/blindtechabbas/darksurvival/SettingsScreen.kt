package com.blindtechabbas.darksurvival.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechabbas.darksurvival.game.GameViewModel
import com.blindtechabbas.darksurvival.game.menuLabels
import com.blindtechabbas.darksurvival.ui.components.MenuSurface

/** Settings. Every line is true: what it says is what the game does. */
@Composable
fun SettingsScreen(vm: GameViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    MenuSurface(
        title = "SETTINGS",
        items = menuLabels(ui),
        index = ui.menuIndex,
        hint = "Swipe right or left to move. Double tap to change. Swipe down to go back.",
        onNext = { vm.menuNext() },
        onPrev = { vm.menuPrev() },
        onSelect = { vm.menuSelect() },
        onRepeat = { vm.menuRepeat() },
        onBack = { vm.onBack() }
    )
}
