package com.blindtechabbas.darksurvival.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * One menu for the whole game. The game speaks every item itself, so no screen reader is needed.
 *  - swipe right: next item      - swipe left: previous item
 *  - one tap: say the current item again
 *  - double tap: choose it       - swipe down: go back
 */
@Composable
fun MenuSurface(
    title: String,
    items: List<String>,
    index: Int,
    hint: String,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSelect: () -> Unit,
    onRepeat: () -> Unit,
    onBack: () -> Unit
) {
    val next = rememberUpdatedState(onNext)
    val prev = rememberUpdatedState(onPrev)
    val select = rememberUpdatedState(onSelect)
    val again = rememberUpdatedState(onRepeat)
    val back = rememberUpdatedState(onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { again.value() },
                    onDoubleTap = { select.value() }
                )
            }
            .pointerInput(Unit) {
                val threshold = 60.dp.toPx()
                var tx = 0f
                var ty = 0f
                detectDragGestures(
                    onDragStart = {
                        tx = 0f
                        ty = 0f
                    },
                    onDragEnd = {
                        if (abs(tx) > threshold && abs(tx) > abs(ty)) {
                            if (tx > 0f) next.value() else prev.value()
                        } else if (ty > threshold && ty > abs(tx)) {
                            back.value()
                        }
                    },
                    onDragCancel = {},
                    onDrag = { change, amount ->
                        change.consume()
                        tx += amount.x
                        ty += amount.y
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            for (i in items.indices) {
                val chosen = i == index
                Text(
                    (if (chosen) "> " else "  ") + items[i],
                    style = MaterialTheme.typography.titleMedium,
                    color = if (chosen) Color(0xFFFFD54F) else Color(0xFFB0BEC5),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF90A4AE),
                textAlign = TextAlign.Center
            )
        }
    }
}
