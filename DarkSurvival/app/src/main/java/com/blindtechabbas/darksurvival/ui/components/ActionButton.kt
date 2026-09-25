package com.blindtechabbas.darksurvival.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A large button for the RIGHT control zone.
 * - Quick tap  -> onTap
 * - Press+hold -> onHoldStart (optional) ... onHoldEnd (optional)
 * Big targets, TalkBack contentDescription on every button.
 */
@Composable
fun ActionButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.35f))
            .border(2.dp, color)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val upOrTimeout = withTimeoutOrNull(400) { waitForUpOrCancellation() }
                    if (upOrTimeout == null && onHoldStart != null) {
                        onHoldStart()
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            released = event.changes.all { it.changedToUp() }
                        }
                        onHoldEnd?.invoke()
                    } else {
                        onTap()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}
