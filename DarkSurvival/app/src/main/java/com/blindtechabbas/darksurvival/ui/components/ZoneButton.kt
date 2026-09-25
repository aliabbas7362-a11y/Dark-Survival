package com.blindtechabbas.darksurvival.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

enum class ZoneAction { TAP, DOUBLE_TAP, LONG_PRESS }

/**
 * One Passthrough-Touch zone with a SINGLE unified gesture handler
 * (no competing handlers, so taps never leak into "hold"):
 * - Quick tap (up within 400ms, no second tap)  -> TAP
 * - Two quick taps                              -> DOUBLE_TAP
 * - Press and HOLD > 400ms                      -> onHoldStart ... onHoldEnd
 */
@Composable
fun ZoneButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onAction: (ZoneAction) -> Unit,
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .background(color.copy(alpha = 0.25f))
            .border(1.dp, color.copy(alpha = 0.6f))
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val upOrTimeout = withTimeoutOrNull(400) { waitForUpOrCancellation() }
                    if (upOrTimeout == null) {
                        // finger held -> continuous action until release
                        onHoldStart?.invoke()
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            released = event.changes.all { it.changedToUp() }
                        }
                        onHoldEnd?.invoke()
                    } else {
                        // quick tap -> check for a second tap (double tap)
                        val second = withTimeoutOrNull(300) { awaitFirstDown(requireUnconsumed = false) }
                        if (second != null) {
                            withTimeoutOrNull(300) { waitForUpOrCancellation() }
                            onAction(ZoneAction.DOUBLE_TAP)
                        } else {
                            onAction(ZoneAction.TAP)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = Color.White)
    }
}
