package com.example.loramessenger.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EmergencyColorScheme = darkColorScheme(
  primary = SignalGreen, secondary = EmergencyAmber, error = EmergencyRed,
  background = NightBackground, surface = NightSurface,
  onBackground = OnNightSurface, onSurface = OnNightSurface,
)

@Composable
fun LoRaMessengerTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = EmergencyColorScheme, typography = Typography, content = content)
}
