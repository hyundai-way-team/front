package com.kscho.way.theme

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

/** 와이어프레임대로 밝은 화면 하나(임시 지도가 밝은 색이라 다크 모드는 아직 안 맞춘다). */
@Composable
fun WayTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = WayColorScheme, typography = Typography, content = content)
}
