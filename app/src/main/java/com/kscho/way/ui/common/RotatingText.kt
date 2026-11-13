package com.kscho.way.ui.common

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

/**
 * 몇 초마다 글자가 굴러 바뀌는 글(shadcn Rotating Text 처럼) — 운전 화면 '1.1km ↔ 09:50 도착 예정'.
 *
 * 글자 하나하나가 [staggerMillis] 씩 늦게 위로 굴러 나가고 새 글자가 아래에서 올라온다. 위·아래 끝은 흐리게 지워
 * 북처럼 돌아가는 느낌을 낸다. 글 내용이 바뀌어도(남은 거리 줄어듦) 같은 방식으로 그 자리 글자만 굴러간다.
 */
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

/** 위·아래 끝 12% 를 흐리게 — 글자가 굴러 넘어갈 때 칼로 자른 듯 보이지 않게. */
private fun Modifier.fadeVerticalEdges(): Modifier =
  graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
    drawContent()
    drawRect(
      Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent),
      blendMode = BlendMode.DstIn,
    )
  }
