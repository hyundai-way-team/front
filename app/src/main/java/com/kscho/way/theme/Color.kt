package com.kscho.way.theme

import androidx.compose.ui.graphics.Color

/** 와이어프레임 v2 디자인 토큰(Figma 변수 이름 그대로) + 주행 화면(D-06~D-10, v1)이 쓰는 옛 이름. */
object WayColors {
  // ── v2 토큰
  val Primary500 = Color(0xFF3B67F6)
  val Primary200 = Color(0xFFBFCEFE)
  val Primary100 = Color(0xFFDBE3FE)
  val Primary50 = Color(0xFFF5F7FF)
  val Grey900 = Color(0xFF0F172A)
  val Grey700 = Color(0xFF334155)
  val Grey600 = Color(0xFF475569)
  val Grey500 = Color(0xFF64748B)
  val Grey400 = Color(0xFF94A3B8)
  val Grey300 = Color(0xFFCBD5E1)
  val Grey200 = Color(0xFFE2E8F0)
  val Grey100 = Color(0xFFF4F8FC)
  val Error600 = Color(0xFFDC2626)
  val Error500 = Color(0xFFEF4444)
  val Error100 = Color(0xFFFEE2E2)
  val Error200 = Color(0xFFFECACA)
  val Error50 = Color(0xFFFEF2F2)
  val Warning700 = Color(0xFFB45309)
  val Warning200 = Color(0xFFFDE68A)
  val Warning50 = Color(0xFFFFFBEB)
  /** D-11 카운트다운 숫자 · 제목(Figma 에 토큰 없이 값으로 들어 있음) */
  val Countdown = Color(0xFF2457D6)
  val DialogTitle = Color(0xFF1F2328)
  /** D-11 뒤를 가리는 막(Grey-900 40%) */
  val Dim = Color(0x660F172A)
  /** 로고·사진 자리(디자인 임시 회색) */
  val Placeholder = Color(0xFFD9D9D9)
  /** 카드 그림자(Shadow Grey-Medium · Grey-Weak) */
  val ShadowMedium = Color(0xFFE2E8F0)
  val ShadowWeak = Color(0xFFF1F5F9)

  // ── 옛 이름(주행 화면 · 지도) — v2 토큰에 맞춘다
  val Blue = Primary500
  val BlueSoft = Primary100
  val BlueBg = Primary50
  val Ink = Grey900
  val Sub = Grey500
  val Line = Grey300
  val Chip = Grey200
  val Panel = Grey100
  val Red = Error600
  val RedBg = Error50
  val Orange = Color(0xFFC2410C)
  val Passenger = Color(0xFFE07F26)
  val Dark = Color(0xFF1F2328)

  // 지도 배경(오프라인 지도가 뜨기 전)
  val MapBg = Color(0xFFEDEEF1)
  val MapBlock = Color(0xFFDDE0E5)
  val MapRoad = Color.White
  val MapPark = Color(0xFFDCEAD9)
}
