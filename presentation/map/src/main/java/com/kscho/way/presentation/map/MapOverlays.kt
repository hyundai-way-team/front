package com.kscho.way.presentation.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kscho.way.presentation.component.theme.WayColors

@Composable
fun MapLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    fontSize = 14.sp,
    color = WayColors.Sub,
    modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp),
  )
}

@Composable
internal fun ZoomButton(text: String, onClick: () -> Unit) {
  Box(
    Modifier.size(48.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(Color.White)
      .border(1.dp, WayColors.Line, RoundedCornerShape(8.dp))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, fontSize = 24.sp, color = WayColors.Ink)
  }
}

@Composable
internal fun CenterPin(pin: MapPin, modifier: Modifier) {
  val look = WayMarkers.lookOf(pin.kind)
  if (look != null) {
    val context = LocalContext.current
    val scale = LocalDensity.current.density
    val label = markerLabel(pin)
    val drawn = remember(look, label, scale) { WayMarkers.draw(context, look, label, scale) }
    val g = drawn.geometry
    Box(modifier.anchorAt(0.dp, 0.dp)) {
      Image(
        drawn.bitmap.asImageBitmap(),
        contentDescription = label,
        modifier = Modifier.anchorAt((-g.anchorX).dp, (-g.anchorY).dp).size(g.width.dp, g.height.dp),
      )
    }
    return
  }
  val color = pinColor(pin.kind)
  Box(modifier.anchorAt(0.dp, 0.dp)) {
    Canvas(Modifier.anchorAt((-20).dp, (-56).dp).size(40.dp, 56.dp)) {
      val r = 18.dp.toPx()
      val tip = Offset(size.width / 2, size.height)
      val head = Offset(size.width / 2, r + 2.dp.toPx())
      drawPath(
        Path().apply {
          moveTo(tip.x, tip.y)
          lineTo(head.x - r * 0.75f, head.y + r * 0.6f)
          lineTo(head.x + r * 0.75f, head.y + r * 0.6f)
          close()
        },
        color,
      )
      drawCircle(color, r, head)
      drawCircle(Color.White, r * 0.35f, head)
    }
    pin.label?.let {
      Text(
        it,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier =
          Modifier.anchorAt(24.dp, (-60).dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp),
      )
    }
  }
}

internal fun Modifier.anchorAt(x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp) =
  layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { placeable.place(x.roundToPx(), y.roundToPx()) }
  }
