package com.kscho.way.presentation.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kscho.way.presentation.component.theme.WayColors

internal val R12 = RoundedCornerShape(12.dp)

internal val R14 = RoundedCornerShape(14.dp)

internal val R16 = RoundedCornerShape(16.dp)

internal val R20 = RoundedCornerShape(20.dp)

internal val R24 = RoundedCornerShape(24.dp)

internal fun Modifier.glass(border: Color = WayColors.Grey200, borderWidth: Dp = 1.dp): Modifier =
  this.shadow(10.dp, R20, ambientColor = WayColors.ShadowMedium, spotColor = WayColors.ShadowMedium)
    .clip(R20)
    .background(Color.White.copy(alpha = 0.86f))
    .border(borderWidth, border, R20)
