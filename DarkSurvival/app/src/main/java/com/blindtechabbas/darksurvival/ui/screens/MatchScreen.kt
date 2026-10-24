package com.blindtechabbas.darksurvival.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechabbas.darksurvival.game.GameViewModel
import com.blindtechabbas.darksurvival.game.Phase
import com.blindtechabbas.darksurvival.game.UiState
import com.blindtechabbas.darksurvival.ui.components.MoveZone
import com.blindtechabbas.darksurvival.ui.components.PressButton

/**
 * The match: helicopter intro, 3-2-1, then play.
 * Playing = LEFT 70% movement zone, RIGHT 30% six buttons (two columns, three rows).
 * The text on screen is only for a sighted helper; the game itself talks and plays sounds.
 */
@Composable
fun MatchScreen(vm: GameViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    when (ui.phase) {
        Phase.INTRO -> IntroView(vm)
        Phase.COUNTDOWN -> CountdownView(ui)
        Phase.PLAYING -> PlayView(vm, ui)
    }
}

@Composable
private fun IntroView(vm: GameViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { vm.skipIntro() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("HELICOPTER", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                "HQ: Agent, drop point reached. Move towards the River Bank, find and eliminate every enemy. " +
                    "You have one day and one night. Out.",
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Text("Tap anywhere to skip", color = Color(0xFFFFD54F), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CountdownView(ui: UiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (ui.countdown > 0) ui.countdown.toString() else "GO",
            style = MaterialTheme.typography.displayLarge,
            color = Color.White
        )
    }
}

@Composable
private fun PlayView(vm: GameViewModel, ui: UiState) {
    val hud = ui.hud
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
    ) {
        Text(
            "HP " + hud.hp + " | " + hud.weapon + " " + hud.ammo + " | Enemies " + hud.enemies + " | " + hud.time,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Text(
            hud.zone + " | Rations " + hud.rations + (if (hud.crouching) " | CROUCHING" else ""),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFFD54F),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            MoveZone(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
                    .padding(4.dp),
                onDown = { vm.moveDown() },
                onUp = { vm.moveUp() },
                onFlickTurn = { dir -> vm.flickTurn(dir) },
                onFlickJump = { vm.flickJump() },
                onTwoFinger = { vm.twoFinger() },
                onSteer = { deg -> vm.steer(deg) },
                onBack = { b -> vm.moveBack(b) }
            )
            Column(
                Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PressButton(
                        label = "SCAN",
                        color = Color(0xFF6A1B9A),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onPress = { vm.scan() }
                    )
                    PressButton(
                        label = "HEAL",
                        color = Color(0xFF2E7D32),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onPress = { vm.heal() }
                    )
                }
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PressButton(
                        label = "WEAPON",
                        color = Color(0xFF1565C0),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onPress = { vm.switchWeapon() }
                    )
                    PressButton(
                        label = "CROUCH",
                        color = Color(0xFF00838F),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onPress = { vm.crouch() }
                    )
                }
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PressButton(
                        label = "FIRE",
                        color = Color(0xFFB71C1C),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onPress = { vm.fireDown() },
                        onRelease = { vm.fireUp() }
                    )
                    PressButton(
                        label = "RELOAD",
                        color = Color(0xFFEF6C00),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onPress = { vm.reload() }
                    )
                }
            }
        }
    }
}
