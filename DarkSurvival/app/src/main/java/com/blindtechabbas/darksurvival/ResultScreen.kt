package com.blindtechabbas.darksurvival.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechabbas.darksurvival.game.GameViewModel
import com.blindtechabbas.darksurvival.game.menuLabels
import com.blindtechabbas.darksurvival.ui.components.MenuSurface

/** After a match: the result, then Play again or Main menu. */
@Composable
fun ResultScreen(vm: GameViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val r = ui.result
    val title = if (r == null) {
        "MATCH OVER"
    } else {
        val head = if (r.won) "YOU SURVIVED" else "GAME OVER"
        val mins = r.seconds / 60
        val secs = r.seconds % 60
        val secText = if (secs < 10) "0" + secs else secs.toString()
        head + "\n" + r.reason + "\nKills " + r.kills + "   Time " + mins + ":" + secText
    }
    MenuSurface(
        title = title,
        items = menuLabels(ui),
        index = ui.menuIndex,
        hint = "Swipe right or left to move. Double tap to choose.",
        onNext = { vm.menuNext() },
        onPrev = { vm.menuPrev() },
        onSelect = { vm.menuSelect() },
        onRepeat = { vm.menuRepeat() },
        onBack = { vm.onBack() }
    )
}
