package com.kscho.way.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kscho.way.R
import com.kscho.way.theme.WayColors
import com.kscho.way.theme.WayType

// 와이어프레임 v2 공통 부품. 화면 폭 1280dp 기준(MainActivity 가 밀도를 맞춘다) — Figma 수치를 그대로 dp 로 쓴다.

private val R12 = RoundedCornerShape(12.dp)
private val R16 = RoundedCornerShape(16.dp)
private val R20 = RoundedCornerShape(20.dp)

/** 화면 틀: 흰 바탕, 바깥 여백 16, 칸 사이 16. */
@Composable
fun ScreenFrame(content: @Composable RowScope.() -> Unit) {
  Row(Modifier.fillMaxSize().background(Color.White).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

/** 왼쪽 흰 판(라운드 20 · 그림자 Grey-Medium · 안쪽 32). [width] 가 없으면 남은 폭을 다 쓴다. */
@Composable
fun RowScope.Panel(width: Dp?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  val sized = if (width != null) Modifier.width(width) else Modifier.weight(1f)
  Column(
    modifier
      .then(sized)
      .fillMaxHeight()
      .shadow(10.dp, R20, ambientColor = WayColors.ShadowMedium, spotColor = WayColors.ShadowMedium)
      .clip(R20)
      .background(Color.White)
      .padding(32.dp),
    verticalArrangement = Arrangement.SpaceBetween,
    content = content,
  )
}

/** 오른쪽 지도 칸(테두리 2 Grey-300 · 라운드 20). */
@Composable
fun RowScope.MapFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  Box(modifier.weight(1f).fillMaxHeight().clip(R20).border(2.dp, WayColors.Grey300, R20), content = content)
}

/** 뒤로 버튼(56 · 테두리 Grey-300 · 라운드 12). */
@Composable
fun BackButton(onClick: () -> Unit) {
  Box(
    Modifier.size(56.dp).clip(R12).background(Color.White).border(1.dp, WayColors.Grey300, R12).clickable(role = Role.Button, onClickLabel = "뒤로", onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(painterResource(R.drawable.ic_back), contentDescription = "뒤로", tint = Color.Unspecified, modifier = Modifier.size(40.dp))
  }
}

/** 단계 표시 1 ··· 2 ··· 3. 지금 단계까지 파란 원, 지금 단계 뒤 점선까지 파랗게. */
@Composable
fun StepDots(step: Int, lineWidth: Dp) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    (1..3).forEach { n ->
      val on = n <= step
      Box(Modifier.size(32.dp).clip(CircleShape).background(if (on) WayColors.Primary500 else WayColors.Grey200), contentAlignment = Alignment.Center) {
        Text("$n", style = WayType.LSemiBold, color = if (on) Color.White else WayColors.Grey500, textAlign = TextAlign.Center)
      }
      if (n < 3) DottedLine(lineWidth, if (n <= step) WayColors.Primary500 else WayColors.Grey300)
    }
  }
}

@Composable
private fun DottedLine(width: Dp, color: Color) {
  Canvas(Modifier.width(width).height(2.dp)) {
    drawLine(
      color,
      Offset(0f, size.height / 2),
      Offset(size.width, size.height / 2),
      strokeWidth = 1.5.dp.toPx(),
      cap = StrokeCap.Round,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
    )
  }
}

/** 단계 머리: 뒤로 · (제목) · 단계 표시. */
@Composable
fun StepHeader(step: Int, onBack: () -> Unit, title: String? = null, lineWidth: Dp = 40.dp) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
      BackButton(onBack)
      if (title != null) Text(title, style = WayType.HeadingM, color = Color.Black)
    }
    StepDots(step, lineWidth)
  }
}

enum class ActionKind {
  PRIMARY,
  OUTLINE,
}

/** 액션 버튼(높이 72 · 라운드 16 · Title/L). 꺼지면 Grey-100 바탕 · Grey-300 글자. */
@Composable
fun ActionButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  kind: ActionKind = ActionKind.PRIMARY,
  @DrawableRes icon: Int? = null,
) {
  val (bg, fg) =
    when {
      !enabled -> WayColors.Grey100 to WayColors.Grey300
      kind == ActionKind.PRIMARY -> WayColors.Primary500 to Color.White
      else -> Color.White to WayColors.Grey700
    }
  Row(
    modifier
      .height(72.dp)
      .clip(R16)
      .background(bg)
      .then(if (enabled && kind == ActionKind.OUTLINE) Modifier.border(1.dp, WayColors.Grey300, R16) else Modifier)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(32.dp))
    Text(text, style = WayType.TitleL, color = fg)
  }
}

/** 칸 이름(L/Medium · Grey-500). */
@Composable
fun FieldLabel(text: String) {
  Text(text, style = WayType.LMedium, color = WayColors.Grey500, modifier = Modifier.fillMaxWidth())
}

/**
 * 검색 칸(라운드 16 · 안쪽 20 · Title/M). 비어 있고 포커스가 없으면 돋보기, 글자가 있으면 지우기(×).
 * 포커스가 있거나 글자가 있으면 테두리가 Primary-500.
 */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
  var focused by remember { mutableStateOf(false) }
  val active = focused || value.isNotEmpty()
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = true,
    textStyle = WayType.TitleM.copy(color = WayColors.Grey900),
    cursorBrush = SolidColor(WayColors.Primary500),
    modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
    decorationBox = { inner ->
      Row(
        Modifier.fillMaxWidth().clip(R16).background(Color.White).border(1.dp, if (active) WayColors.Primary500 else WayColors.Grey300, R16).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!focused && value.isEmpty()) Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
        Box(Modifier.weight(1f)) {
          if (value.isEmpty()) Text(placeholder, style = WayType.TitleM, color = WayColors.Grey400)
          inner()
        }
        if (value.isNotEmpty()) {
          Icon(
            painterResource(R.drawable.ic_clear),
            contentDescription = "지우기",
            tint = Color.Unspecified,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button) { onValueChange("") },
          )
        }
      }
    },
  )
}

/** 회색 안내 상자(•  줄들 · L/Regular · Grey-600). */
@Composable
fun BulletBox(lines: List<String>, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth().clip(R16).background(WayColors.Grey100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    lines.forEach { Text("•  $it", style = WayType.LRegular, color = WayColors.Grey600) }
  }
}

enum class Tone {
  ERROR,
  WARNING,
  NEUTRAL,
}

/** 상태 칩(점 · M/Medium). 정차 금지 구간 = ERROR, 정차 규제 미확인 = NEUTRAL. */
@Composable
fun StatusChip(text: String, tone: Tone) {
  val (bg, fg) =
    when (tone) {
      Tone.ERROR -> WayColors.Error50 to WayColors.Error600
      Tone.WARNING -> WayColors.Warning50 to WayColors.Warning700
      Tone.NEUTRAL -> WayColors.Grey100 to WayColors.Grey500
    }
  Row(Modifier.clip(CircleShape).background(bg).padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).clip(CircleShape).background(fg)) }
    Text(text, style = WayType.MMedium, color = fg, modifier = Modifier.padding(start = 2.dp))
  }
}

/** 경고 상자(!) — ERROR 는 정차 금지 구간, 그 밖엔 노랑(현장 표지판 확인). */
@Composable
fun AlertBox(text: String, tone: Tone, modifier: Modifier = Modifier) {
  val error = tone == Tone.ERROR
  val fg = if (error) WayColors.Error600 else WayColors.Warning700
  Row(
    modifier
      .fillMaxWidth()
      .clip(R16)
      .background(if (error) WayColors.Error50 else WayColors.Warning50)
      .border(1.dp, if (error) WayColors.Error200 else WayColors.Warning200, R16)
      .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val icon = if (error) R.drawable.ic_alert_error else R.drawable.ic_alert_warning
    Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
    Text(text, style = WayType.LRegular, color = fg, modifier = Modifier.weight(1f))
  }
}

/** 사람 사진 자리(원 · 디자인 임시 회색). */
@Composable
fun Avatar(size: Dp = 48.dp) {
  Box(Modifier.size(size).clip(CircleShape).background(WayColors.Grey200))
}

/** 흰 카드(테두리 Grey-200 · 라운드 16 · 그림자 Grey-Weak). [pressed] 면 Primary-50. */
fun Modifier.whiteCard(pressed: Boolean = false): Modifier =
  this.shadow(4.dp, R16, ambientColor = WayColors.ShadowWeak, spotColor = WayColors.ShadowWeak)
    .clip(R16)
    .background(if (pressed) WayColors.Primary50 else Color.White)
    .border(1.dp, WayColors.Grey200, R16)
