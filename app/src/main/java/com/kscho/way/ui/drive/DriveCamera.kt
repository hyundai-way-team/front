package com.kscho.way.ui.drive

/**
 * D-06·D-07 주행 지도의 확대. 멀 땐 넓게 보다가, 카드에 보이는 남은 거리(픽업 위치까지 남은 길)가
 * [ZOOM_IN_REMAINING_METERS] 이하가 되면 **한 번만** 확대한다 — 점점 확대하지 않고, 길을 다시 잡아 남은 거리가 늘어도 되돌리지 않는다.
 */
internal object DriveCamera {
  /** 멀 때(화면 1dp = 1.6 m) — 출발 직후에도 차 둘레 길이 보이게. */
  const val FAR_METERS_PER_DP = 1.6f

  /** 남은 거리가 이만큼 이하가 되면 확대한다. */
  const val ZOOM_IN_REMAINING_METERS = 500

  /** 확대한 뒤(1dp = 0.6 m) — 남은 500 m 쯤이 차 둘레 화면에 들어온다. */
  const val CLOSE_METERS_PER_DP = 0.6f

  /** 정차 화면(D-08)의 확대(1dp = 0.3 m). */
  const val NEAR_METERS_PER_DP = 0.3f

  /** 지금 확대해 있어야 하는지 — 한 번 확대하면 그대로([wasZoomedIn]). 남은 거리를 모르면 하던 대로. */
  fun zoomedIn(wasZoomedIn: Boolean, remainingMeters: Int?): Boolean =
    wasZoomedIn || (remainingMeters != null && remainingMeters <= ZOOM_IN_REMAINING_METERS)

  fun metersPerDp(zoomedIn: Boolean): Float = if (zoomedIn) CLOSE_METERS_PER_DP else FAR_METERS_PER_DP
}
