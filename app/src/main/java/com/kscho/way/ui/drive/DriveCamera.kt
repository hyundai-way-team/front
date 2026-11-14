package com.kscho.way.ui.drive

/**
 * D-06·D-07 주행 지도의 확대. 멀 땐 지금처럼 넓게 보다가, 픽업 위치에 가까워질수록 확대한다.
 *
 * 카메라는 차를 왼쪽 상태 카드에 안 가린 곳의 가운데에 둔다. 차~픽업 위치 거리가 그 가운데에서 보이는 가장 짧은
 * 가장자리까지의 80% 가 되게 맞추므로, 확대하는 동안 픽업 위치가 화면(카드 밖) 안에 남는다.
 */
internal object DriveCamera {
  /** 멀 때(화면 1dp = 1.6 m) — 출발 직후에도 차 둘레 길이 보이게. */
  const val FAR_METERS_PER_DP = 1.6f

  /** 가장 가까이(1dp = 0.3 m) — 정차 화면(D-08)과 같아서 도착할 때 확대가 튀지 않는다. */
  const val NEAR_METERS_PER_DP = 0.3f

  /** 픽업 위치를 보이는 가장자리에서 띄우는 정도(1.25 → 가장자리까지의 80% 자리). */
  private const val EDGE_MARGIN = 1.25

  /**
   * @param distanceMeters 차 → 픽업 위치 곧은 거리
   * @param widthDp 지도 너비 · [heightDp] 지도 높이
   * @param coveredStartDp 지도 왼쪽에서 상태 카드가 가리는 너비(카메라는 그 오른쪽의 가운데를 본다)
   */
  fun metersPerDp(distanceMeters: Double, widthDp: Float, heightDp: Float, coveredStartDp: Float): Float {
    val halfViewDp = minOf((widthDp - coveredStartDp) / 2f, heightDp / 2f)
    if (halfViewDp <= 0f) return FAR_METERS_PER_DP
    return (distanceMeters * EDGE_MARGIN / halfViewDp).toFloat().coerceIn(NEAR_METERS_PER_DP, FAR_METERS_PER_DP)
  }
}
