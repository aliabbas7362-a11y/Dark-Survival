package com.blindtechabbas.darksurvival.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DarkGreen,
    secondary = Gold,
    tertiary = SteelBlue,
    background = NightBlack,
    surface = NightBlack,
    error = BloodRed
)

@Composable
fun DarkSurvivalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
