package com.kscho.way.domain.model

/** 운전자가 확정한 정차 지점(= 픽업 위치). */
data class PickupPoint(
  val location: LatLng,
  val description: LocationDescription,
  /** 검색한 약속 장소에서 떨어진 거리(m). 약속 장소 없이 정했으면 null. */
  val distanceFromPlaceMeters: Int?,
)

/** 출발 직전에 확인한 픽업 계획(D-05). */
data class PickupPlan(
  val passenger: Contact,
  val place: Place?,
  val pickupPoint: PickupPoint,
  val restriction: StopRestriction,
  val route: RoutePreview,
  val vehicle: MyVehicle,
  /** 픽업 뒤 갈 곳(D-05 목적지 설정, 없어도 출발). 탑승하면 여기로 안내를 이어 간다. */
  val destination: Place? = null,
)

enum class BoardingSource {
  /** 도어 열림·닫힘 */
  DOOR,
  /** 도어 이벤트를 못 쓸 때의 '탑승 완료' 버튼 */
  MANUAL,
  /** Gleo "탑승 완료" */
  VOICE,
}

/** 픽업이 끝났을 때 서버에 알리는 결과(가설 검증용 지표, 기획 3-11) — 동승자 정보 없이 숫자만. */
data class PickupOutcome(
  val boarded: Boolean,
  val boardedAtMillis: Long?,
  /** 정차 후 탑승까지 걸린 초. 정차 전에 탔으면 null. */
  val waitedSeconds: Int?,
  /** 앱 안에 위치 변경 기능이 없어 지금은 늘 0 (앱 밖 변경 기록 방법은 미정). */
  val pickupLocationChanges: Int,
  val boardingSource: BoardingSource?,
)

/** 지난 픽업(홈 '최근 픽업' · 같은 사람을 같은 곳에서 다시 태울 때 불러오기). 앱에만 둔다. */
data class PickupRecord(
  val id: String,
  val passenger: Contact,
  val place: Place?,
  val pickupPoint: PickupPoint,
  val boarded: Boolean,
  val pickupLocationChanges: Int,
  val waitedSeconds: Int?,
  val endedAtMillis: Long,
)
