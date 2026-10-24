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

/**
 * A button for the match. onPress runs the moment the finger touches it,
 * onRelease (optional) runs when the finger lifts. There is no long-press
 * trick any more (the old RELOAD hold used to eat a ration by mistake).
 */
@Composable
fun PressButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    onRelease: (() -> Unit)? = null
) {
    val press = rememberUpdatedState(onPress)
    val release = rememberUpdatedState(onRelease)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.35f))
            .border(2.dp, color)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    press.value()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                    }
                    release.value?.invoke()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
