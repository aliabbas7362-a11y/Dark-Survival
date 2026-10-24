package com.blindtechabbas.darksurvival.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.blindtechabbas.darksurvival.game.Cfg
import kotlin.math.abs

/**
 * The left 70% of the match screen.
 *  - finger down: walk forward (starts after a very short hold, so a flick never makes a step)
 *  - quick flick left or right: turn 30 degrees
 *  - quick flick up: jump
 *  - a second finger touching: jump (the first finger keeps walking)
 *  - keep the finger down and drag left or right: steer while walking
 *  - keep the finger down and drag down: walk backward
 * TalkBack must be off for these touches to reach the game (the start gate takes care of that).
 */
@Composable
fun MoveZone(
    modifier: Modifier = Modifier,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onFlickTurn: (Int) -> Unit,
    onFlickJump: () -> Unit,
    onTwoFinger: () -> Unit,
    onSteer: (Float) -> Unit,
    onBack: (Boolean) -> Unit
) {
    val down = rememberUpdatedState(onDown)
    val up = rememberUpdatedState(onUp)
    val flickTurn = rememberUpdatedState(onFlickTurn)
    val flickJump = rememberUpdatedState(onFlickJump)
    val two = rememberUpdatedState(onTwoFinger)
    val steer = rememberUpdatedState(onSteer)
    val back = rememberUpdatedState(onBack)

    Box(
        modifier = modifier
            .background(Color(0xFF1B5E20).copy(alpha = 0.3f))
            .border(2.dp, Color(0xFF1B5E20))
            .pointerInput(Unit) {
                val flickPx = 70.dp.toPx()
                val backPx = 80.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val firstId = first.id
                    val t0 = first.uptimeMillis
                    val startY = first.position.y
                    var lastX = first.position.x
                    var lastY = first.position.y
                    var sumX = 0f
                    var sumY = 0f
                    var flicked = false
                    var maxPointers = 1
                    var backNow = false
                    down.value()
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed > maxPointers) {
                            maxPointers = pressed
                            if (pressed == 2) two.value()
                        }
                        val c = event.changes.firstOrNull { it.id == firstId }
                        if (c != null && c.pressed) {
                            val dx = c.position.x - lastX
                            val dy = c.position.y - lastY
                            lastX = c.position.x
                            lastY = c.position.y
                            sumX += dx
                            sumY += dy
                            val age = c.uptimeMillis - t0
                            if (!flicked && age <= 280L) {
                                if (sumY < -flickPx && -sumY > 1.5f * abs(sumX)) {
                                    flicked = true
                                    flickJump.value()
                                } else if (abs(sumX) > flickPx && abs(sumX) > 1.5f * abs(sumY)) {
                                    flicked = true
                                    flickTurn.value(if (sumX > 0f) 1 else -1)
                                }
                            } else if (!flicked) {
                                steer.value(dx * Cfg.STEER_DEG_PER_PX)
                                val rel = c.position.y - startY
                                if (!backNow && rel > backPx) {
                                    backNow = true
                                    back.value(true)
                                } else if (backNow && rel < backPx * 0.4f) {
                                    backNow = false
                                    back.value(false)
                                }
                            }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (backNow) back.value(false)
                    up.value()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text("MOVE", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
