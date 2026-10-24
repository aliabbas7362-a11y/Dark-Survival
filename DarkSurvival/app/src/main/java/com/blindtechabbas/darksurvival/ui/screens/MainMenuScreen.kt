package com.blindtechabbas.darksurvival.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechabbas.darksurvival.game.GameViewModel
import com.blindtechabbas.darksurvival.game.menuLabels
import com.blindtechabbas.darksurvival.ui.components.MenuSurface

/** Main menu: Survive 24 Hours, Settings, Exit. The game speaks every item. */
@Composable
fun MainMenuScreen(vm: GameViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    MenuSurface(
        title = "DARK SURVIVAL",
        items = menuLabels(ui),
        index = ui.menuIndex,
        hint = "Swipe right or left to move. Double tap to choose. Tap to hear the item again.",
        onNext = { vm.menuNext() },
        onPrev = { vm.menuPrev() },
        onSelect = { vm.menuSelect() },
        onRepeat = { vm.menuRepeat() },
        onBack = { vm.onBack() }
    )
}
