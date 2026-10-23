package com.blindtechabbas.darksurvival.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechabbas.darksurvival.game.GameViewModel

/**
 * The start gate. This game talks by itself, so TalkBack must be off or suspended.
 * The game checks it, tells the player in its own voice, and waits.
 * If TalkBack is already off but the phone still reports it as on, a double tap lets the player in.
 */
@Composable
fun GateScreen(vm: GameViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { vm.gateOverride() })
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
                "DARK SURVIVAL",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (ui.talkBackOn) {
                    "TalkBack is ON. This game talks by itself. Suspend or turn off TalkBack to play."
                } else {
                    "Starting..."
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFFD54F),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "If TalkBack is already off, double tap the screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center
            )
            if (!ui.ttsOk) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "The game voice (text to speech) did not start. Install or enable a speech engine in the phone settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFEF5350),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
