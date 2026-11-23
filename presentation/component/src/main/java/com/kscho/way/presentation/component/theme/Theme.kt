package com.kscho.way.presentation.component.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WayColorScheme =
  lightColorScheme(
    primary = WayColors.Blue,
    onPrimary = Color.White,
    secondary = WayColors.Sub,
    background = Color.White,
    surface = Color.White,
    onBackground = WayColors.Ink,
    onSurface = WayColors.Ink,
    error = WayColors.Red,
  )

@Composable
fun WayTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = WayColorScheme, typography = Typography, content = content)
}
