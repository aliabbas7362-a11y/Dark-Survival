package com.blindtechabbas.darksurvival.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blindtechabbas.darksurvival.game.GameViewModel

@Composable
fun MainMenuScreen(viewModel: GameViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("DARK SURVIVAL", style = MaterialTheme.typography.titleLarge)
        Text("Blind-friendly battle game", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.goToChapterSelect() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Start Game")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.goToSettings() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Settings")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.goToMatch(1) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Quick Match - Chapter 1")
        }
        Spacer(Modifier.height(24.dp))
        Text("Designed by Abbas Ali", style = MaterialTheme.typography.bodyMedium)
    }
}
