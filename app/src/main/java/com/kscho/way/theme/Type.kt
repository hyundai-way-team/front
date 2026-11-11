package com.kscho.way.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kscho.way.R

/** 와이어프레임 v2 글꼴 — Pretendard(OFL). 쓰는 굵기 4가지만 넣는다. */
val Pretendard =
  FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
  )

/** v2 글자 스타일(Figma 텍스트 스타일 이름 그대로). 자간 -2%(작은 글자 -1%). */
object WayType {
  private fun style(size: Int, weight: FontWeight, line: Double, tracking: Double = -0.02) =
    TextStyle(fontFamily = Pretendard, fontSize = size.sp, fontWeight = weight, lineHeight = line.em, letterSpacing = tracking.em)

  val HeadingL = style(36, FontWeight.Bold, 1.3)
  val HeadingM = style(32, FontWeight.Bold, 1.4)
  val TitleXL = style(24, FontWeight.Bold, 1.4)
  val TitleL = style(22, FontWeight.SemiBold, 1.4)
  val TitleM = style(20, FontWeight.SemiBold, 1.4)
  val LSemiBold = style(18, FontWeight.SemiBold, 1.5)
  val LMedium = style(18, FontWeight.Medium, 1.5)
  val LRegular = style(18, FontWeight.Normal, 1.5)
  val MMedium = style(16, FontWeight.Medium, 1.5)
  val MRegular = style(16, FontWeight.Normal, 1.5)
  val SRegular = style(14, FontWeight.Normal, 1.6, -0.01)
}

// 따로 정하지 않은 글자(주행 화면 등)도 Pretendard 로
val Typography =
  Typography(
    bodyLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    labelLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
  )
