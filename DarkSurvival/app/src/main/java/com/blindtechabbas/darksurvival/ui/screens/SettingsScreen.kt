package com.blindtechabbas.darksurvival.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechabbas.darksurvival.game.GameViewModel

@Composable
fun SettingsScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("TTS Engine: Google TTS (English)", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text("3D Spatial Audio: Enabled", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text("TalkBack Passthrough: Enabled", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text("Sound Effects: 18 real sounds", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))

        // ---- GAME ANNOUNCEMENTS master toggle (v1.9) ----
        // Wired directly to GameViewModel.announce() — the single entry
        // point of every voice line. OFF = total silence (no approach,
        // no telegraph, no hit, no UI lines), ON = everything back.
        Button(
            onClick = { viewModel.toggleAnnouncements() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.announcementsEnabled) Color(0xFF2E7D32) else Color(0xFFB71C1C)
            )
        ) {
            Text(if (state.announcementsEnabled) "Game Announcements: ON" else "Game Announcements: OFF")
        }
        Spacer(Modifier.height(12.dp))

        // ---- SENSOR MODE toggle (v2.6) — tilt steering, persisted ----
        Button(
            onClick = { viewModel.toggleSensor() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.sensorEnabled) Color(0xFF2E7D32) else Color(0xFF616161)
            )
        ) {
            Text(if (state.sensorEnabled) "Sensor Mode (Tilt Steering): ON" else "Sensor Mode (Tilt Steering): OFF")
        }
        Spacer(Modifier.height(12.dp))

        // ---- CALIBRATE SENSOR (v2.8): captures current angle as neutral ----
        Button(
            onClick = { viewModel.calibrateSensor() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
        ) {
            Text("Calibrate Sensor (Set Neutral Position)")
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { viewModel.toggleSound() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (state.soundEnabled) "Sound: ON" else "Sound: OFF")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.goToMainMenu() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Back")
        }
    }
}
