package com.blindtechabbas.darksurvival.ui.screens

import android.content.Context
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechabbas.darksurvival.game.GameViewModel
import com.blindtechabbas.darksurvival.game.label
import com.blindtechabbas.darksurvival.ui.components.ActionButton

private fun vibrate(context: Context) {
    try {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        v?.vibrate(40)
    } catch (_: Exception) {
    }
}

/**
 * Match Screen — LANDSCAPE STRICT TWO-ZONE controls (v2.7).
 * LEFT 70% = MOVEMENT ONLY: finger DOWN = walk forward instantly,
 *              quick 1-finger swipe left/right = lane change (wrap-around),
 *              drag down = walk back, release = stop.
 *              ZERO crouch logic here — swiping never crouches.
 * RIGHT 30% = buttons. The ACTION button is the ONLY crouch/stand toggle.
 * HUD on top. GAME OVER / WIN = opaque result screens (sound stopped).
 */
@Composable
fun MatchScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // ---- WIN SCREEN: mirrors the Lose screen exactly (v1.8) ----
    // All enemies down = Survive Complete. Same teardown: loop already
    // cancelled in checkVictory(), ambience + SFX stopped by the Activity.
    if (state.victory) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0F)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
                Text("SURVIVE COMPLETE", style = MaterialTheme.typography.titleLarge, color = Color(0xFF2E7D32))
                Spacer(Modifier.height(12.dp))
                Text("Enemies killed: ${state.kills} of ${state.totalEnemies}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Time: ${state.elapsedSec} seconds", color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Chapter ${state.chapterId}", color = Color.White)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.restart() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Play Chapter ${state.chapterId} Again") }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.goToMainMenu() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Main Menu") }
            }
        }
        return
    }

    if (state.gameOver) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0F)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
                Text("GAME OVER", style = MaterialTheme.typography.titleLarge, color = Color(0xFFB71C1C))
                Spacer(Modifier.height(12.dp))
                Text("Enemies killed: ${state.kills}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Chapter ${state.chapterId}", color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("You were shot by enemies", color = Color.White)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.restart() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Restart Chapter ${state.chapterId}") }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.goToMainMenu() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Main Menu") }
            }
        }
        return
    }

    val context = LocalContext.current

    // ---- GRENADE PULSING HAPTIC ALERT (v2.5) — continuous pulsing warning ----
    // A repeating pulse waveform while a grenade is live (grenadeLane != null);
    // the player feels the tick-tick-tick while listening for where it landed.
    LaunchedEffect(state.grenadeEventId) {
        if (state.grenadeLane != null) {
            try {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // 4 pulses of 90ms separated by 160ms gaps — ~1s pattern
                    val timings = longArrayOf(0, 90, 160, 90, 160, 90, 160, 90, 160)
                    v?.vibrate(android.os.VibrationEffect.createWaveform(timings, -1))
                } else {
                    @Suppress("DEPRECATION") v?.vibrate(400)
                }
            } catch (_: Exception) {
            }
        }
    }

    // ---- ENEMY DEATH HAPTIC (v2.4) — 100% clear death confirmation ----
    // A strong, short pulse on every death transition (deathEventId bumps
    // once per killed enemy). VibrationEffect with a proper API-26 check.
    LaunchedEffect(state.deathEventId) {
        if (state.deathEventId > 0) {
            try {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v?.vibrate(
                        android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION") v?.vibrate(120)
                }
            } catch (_: Exception) {
            }
        }
    }

    // ---- HELICOPTER + PARACHUTE INTRO (PUBG-style, v1.9) ----
    // Blocks gameplay until the descent completes. Tap anywhere to skip.
    if (state.intro) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0F))
                .pointerInput(Unit) { detectTapGestures(onTap = { viewModel.skipIntro() }) },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HELICOPTER", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("Rotor thumping... climbing into the night sky", color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Jumping from the helicopter...", color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Parachute open... descending into the drop zone", color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Text("Tap anywhere to skip", color = Color(0xFFFFD54F), textAlign = TextAlign.Center)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
        // ---- HUD ----
        val alive = state.enemies.filter { it.health > 0 }
        val nearest = alive.minByOrNull { it.distance }
        val zoneName = com.blindtechabbas.darksurvival.game.MAP_ZONES[state.currentZone].name
        val status = when {
            nearest == null -> "Clear"
            nearest.distance < 0.3f -> "Attacking"
            nearest.distance < 0.6f -> "Close"
            else -> "Distant"
        }
        val nearestText = nearest?.let {
            "${it.archetype.displayName} ${it.weapon.displayName} ${it.lane.label()} ${(it.distance * 100).toInt()}%"
        } ?: "none"
        Text(
            "Ch ${state.chapterId} | HP ${state.player.health} | ${state.player.weapon.displayName} ${state.player.ammo}/${state.player.weapon.maxAmmo} | Ration ${state.player.rationCount}",
            style = MaterialTheme.typography.bodyMedium, color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Text(
            "$zoneName (${state.player.progress}%) | Enemies ${alive.size} of ${state.totalEnemies} | Nearest: $nearestText | $status",
            style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFD54F),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Text(
            state.lastAnnouncement,
            style = MaterialTheme.typography.bodyMedium, color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
        )

        // ---- Gesture layer (LEFT 70%) + buttons (RIGHT 30%) ----
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // LEFT 70% — ISOLATED gesture layer: absorbs movement inputs only,
            // never reaches the right-side buttons.
            //  · QUICK swipe left/right = INSTANT lane change (WRAP-AROUND:
            //    Left->Center->Right->Left…, like music tracks)
            //  · HOLD = continuous forward walk; release = stop
            //  · drag down = walk back; drag up again = forward
            //  · quick tap (no hold, minimal movement) = crouch/stand toggle
            // NO tutorial text — pure audio + haptics guidance.
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .background(Color(0xFF1B5E20).copy(alpha = 0.3f))
                    .border(2.dp, Color(0xFF1B5E20))
                    .clearAndSetSemantics { }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            // v2.9 — TALKBACK-SAFE MULTI-PART SWIPES (exact spec):
                            //  · Swipe Right-then-Left (one motion) = Move LEFT
                            //  · Swipe Left-then-Right (one motion) = Move RIGHT
                            //  · Swipe Down-then-Up & HOLD = walk FORWARD continuously
                            //  · Swipe Up-then-Down & HOLD = walk BACKWARD continuously
                            //  · Finger UP = movement AND footstep audio stop instantly.
                            var horizPhase = 0   // 1 = saw right swipe, -1 = saw left swipe
                            var vertPhase = 0    // 1 = saw down swipe, -1 = saw up swipe
                            var walking = false
                            var accX = 0f
                            var accY = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isNotEmpty()) {
                                    val dx = pressed.fold(0f) { acc, c -> acc + c.positionChange().x } / pressed.size
                                    val dy = pressed.fold(0f) { acc, c -> acc + c.positionChange().y } / pressed.size
                                    pressed.forEach { it.consume() }
                                    accX += dx
                                    accY += dy
                                    // ---- horizontal reversal = lane change (wrap-around) ----
                                    if (accX > 55f && horizPhase == 0) { horizPhase = 1; accX = 0f }
                                    if (accX < -55f && horizPhase == 0) { horizPhase = -1; accX = 0f }
                                    if (horizPhase == 1 && accX < -55f) {
                                        viewModel.moveLane(-1); horizPhase = 0; accX = 0f; vibrate(context)
                                    }
                                    if (horizPhase == -1 && accX > 55f) {
                                        viewModel.moveLane(1); horizPhase = 0; accX = 0f; vibrate(context)
                                    }
                                    // ---- vertical reversal + HOLD = continuous walking ----
                                    if (accY > 55f && vertPhase == 0) { vertPhase = 1; accY = 0f }
                                    if (accY < -55f && vertPhase == 0) { vertPhase = -1; accY = 0f }
                                    if (vertPhase == 1 && accY < -55f && !walking) {
                                        // Down-then-Up & HOLD = FORWARD
                                        walking = true
                                        viewModel.startWalking()
                                        vibrate(context)
                                        accY = 0f
                                    }
                                    if (vertPhase == -1 && accY > 55f && !walking) {
                                        // Up-then-Down & HOLD = BACKWARD
                                        walking = true
                                        viewModel.startWalkingBack()
                                        vibrate(context)
                                        accY = 0f
                                    }
                                }
                                if (pressed.isEmpty()) break
                            }
                            // Finger up: movement stops AND the in-flight footstep
                            // stream is cut the exact same millisecond.
                            viewModel.stopWalking()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("MOVE", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.width(4.dp))

            // RIGHT 30% — buttons; the gesture layer never intercepts them
            Column(
                Modifier.weight(0.3f).fillMaxHeight().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionButton(
                    label = "FIRE", color = Color(0xFFB71C1C),
                    onTap = { viewModel.fire() },
                    onHoldStart = { viewModel.startFire() },
                    onHoldEnd = { viewModel.stopFire() },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                ActionButton(
                    label = "RELOAD", color = Color(0xFFEF6C00),
                    onTap = { viewModel.reload() },
                    onHoldStart = { viewModel.eatRation() },
                    onHoldEnd = null,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                ActionButton(
                    label = "MODE", color = Color(0xFF1565C0),
                    onTap = { viewModel.switchWeapon(true) },
                    onHoldStart = null,
                    onHoldEnd = null,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                ActionButton(
                    label = "CROUCH", color = Color(0xFF6A1B9A),
                    onTap = { viewModel.action() },
                    onHoldStart = null,
                    onHoldEnd = null,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}
