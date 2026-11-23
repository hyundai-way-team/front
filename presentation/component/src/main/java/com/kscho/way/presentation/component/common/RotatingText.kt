package com.kscho.way.presentation.component.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

@Composable
fun RotatingText(
  texts: List<String>,
  style: TextStyle,
  color: Color,
  modifier: Modifier = Modifier,
  intervalMillis: Long = 5_000L,
  staggerMillis: Int = 30,
) {
  if (texts.isEmpty()) return
  var index by remember { mutableIntStateOf(0) }
  LaunchedEffect(texts.size, intervalMillis) {
    while (true) {
      delay(intervalMillis)
      index = (index + 1) % texts.size
    }
  }
  val current = texts[index % texts.size]
  val slots = texts.maxOf { it.length }
  Row(modifier.clipToBounds().fadeVerticalEdges(), verticalAlignment = Alignment.CenterVertically) {
    for (i in 0 until slots) {
      AnimatedContent(
        targetState = current.getOrNull(i)?.toString() ?: "",
        transitionSpec = {
          val wait = i * staggerMillis
          (slideInVertically(tween(ROLL_MILLIS, wait, FastOutSlowInEasing)) { it } + fadeIn(tween(FADE_MILLIS, wait)))
            .togetherWith(slideOutVertically(tween(ROLL_MILLIS, wait, FastOutSlowInEasing)) { -it } + fadeOut(tween(FADE_MILLIS, wait)))
            .using(SizeTransform(clip = false) { _, _ -> tween(ROLL_MILLIS, wait, FastOutSlowInEasing) })
        },
        label = "rotating-text-$i",
      ) { ch ->
        Text(ch, style = style, color = color, maxLines = 1, softWrap = false)
      }
    }
  }
}

private const val ROLL_MILLIS = 450
private const val FADE_MILLIS = 300

private fun Modifier.fadeVerticalEdges(): Modifier =
  graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
    drawContent()
    drawRect(
      Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent),
      blendMode = BlendMode.DstIn,
    )
  }
