package com.kscho.way.domain.model

/** 정차 금지 구간(버스정류장·횡단보도 등). [rule] 예) "절대 주정차 금지 · 상시" */
data class RestrictedZone(val id: String, val name: String, val rule: String, val path: List<LatLng>)

/** 도착 예정 시각 기준 정차 규제 확인 결과. */
sealed interface StopRestriction {
  /** 데이터에서 규제를 찾지 못함 — 데이터에 없는 규제가 있을 수 있어 현장 표지판 확인을 안내한다. */
  data object NotFound : StopRestriction

  data class NoStopping(val zone: RestrictedZone) : StopRestriction
}
