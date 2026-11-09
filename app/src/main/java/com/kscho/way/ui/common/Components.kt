package com.kscho.way.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kscho.way.theme.WayColors

/** 출발 전 단계 머리(뒤로 · 1 동승자 · 2 픽업 위치 · 3 확인 · 출발). */
@Composable
fun StepHeader(step: Int, onBack: () -> Unit, modifier: Modifier = Modifier, title: String? = null) {
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    OutlinedButton(
      onClick = onBack,
      modifier = Modifier.size(56.dp),
      shape = RoundedCornerShape(8.dp),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
      border = BorderStroke(1.dp, WayColors.Line),
    ) {
      Text("‹", fontSize = 28.sp, color = WayColors.Ink)
    }
    Spacer(Modifier.width(24.dp))
    if (title != null) {
      Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("1 동승자", "2 픽업 위치", "3 확인 · 출발").forEachIndexed { i, label ->
        val on = i + 1 == step
        Text(
          label,
          color = if (on) Color.White else WayColors.Sub,
          fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
          fontSize = 15.sp,
          modifier = Modifier.clip(CircleShape).background(if (on) WayColors.Blue else WayColors.Chip).padding(horizontal = 14.dp, vertical = 8.dp),
        )
      }
    }
  }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.height(72.dp),
    shape = RoundedCornerShape(12.dp),
    colors = ButtonDefaults.buttonColors(containerColor = WayColors.Blue),
  ) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
  }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, WayColors.Line)) {
    Text(text, fontSize = 18.sp, color = WayColors.Ink)
  }
}

/** 이름 앞 동그라미(사진 자리). */
@Composable
fun Avatar(selected: Boolean = false, modifier: Modifier = Modifier) {
  Box(modifier.size(44.dp).clip(CircleShape).background(if (selected) WayColors.BlueSoft else WayColors.Chip))
}

/** 회색 안내 상자(• 줄들). */
@Composable
fun NoteBox(lines: List<String>, modifier: Modifier = Modifier) {
  androidx.compose.foundation.layout.Column(
    modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(WayColors.Panel).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    lines.forEach { Text("• $it", fontSize = 15.sp, color = WayColors.Sub) }
  }
}

/** 상태 점 + 글(규제 확인 칩 등). */
@Composable
fun DotChip(text: String, dot: Color, background: Color, textColor: Color, modifier: Modifier = Modifier) {
  Row(
    modifier.clip(CircleShape).background(background).padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
    Spacer(Modifier.width(6.dp))
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
  }
}

fun Modifier.card(selected: Boolean = false): Modifier =
  this.clip(RoundedCornerShape(10.dp))
    .background(if (selected) WayColors.BlueBg else Color.White)
    .border(if (selected) 2.dp else 1.dp, if (selected) WayColors.Blue else WayColors.Line, RoundedCornerShape(10.dp))
