package com.kscho.way.data.remote

import kotlinx.coroutines.flow.Flow

/**
 * Way 서버 API (운전자 앱 쪽). 서버 명세가 오기 전 클라이언트가 가정한 모양 — 서버 담당과 맞출 것.
 * 지금은 [com.kscho.way.data.mock.MockWayApi] 로 시연하고, 서버가 생기면 HTTP 구현만 갈아 끼운다.
 *
 * 경로는 제안이다.
 */
interface WayApi {
  // ── 장소·지도 (외부 지도/장소 API 를 서버가 감싼다고 가정)

  /** GET /places?query= — 장소명·주소 둘 다로 검색 */
  suspend fun searchPlaces(query: String): List<PlaceDto>

  /** GET /places/reverse?lat=&lng= — 핀 좌표 → "신촌 XX 빌라 골목 입구 앞" / "신촌로 · 도로 북쪽 인도" */
  suspend fun describeLocation(location: LatLngDto): LocationDescriptionDto

  /** GET /map/features?lat=&lng=&radius= — 임시 지도용(지도 SDK 를 정하면 빠질 수 있다) */
  suspend fun mapFeatures(center: LatLngDto, radiusMeters: Int): MapFeaturesDto

  /** GET /stop-restrictions/check?lat=&lng=&arrivalAt= — 도착 예정 시각 기준 정차 금지 구간인지 */
  suspend fun checkStopRestriction(location: LatLngDto, arrivalAtEpochMillis: Long): StopRestrictionDto

  /** GET /me/vehicle — 차량 설명·번호판(Pleos CarInfo 엔 번호판이 없다) */
  suspend fun myVehicle(): VehicleDto

  // ── 픽업 세션

  /** POST /pickups — 세션 생성. 서버가 동승자에게 Pleos 문자(차량 번호·픽업 위치·ETA·참여 링크)를 보낸다. */
  suspend fun createPickup(request: CreatePickupRequest): CreatePickupResponse

  /** WS(또는 SSE·폴링) /pickups/{id}/passenger — 링크 확인·위치 공유·동승자 위치 */
  fun passengerUpdates(pickupId: String): Flow<PassengerStatusDto>

  /** POST /pickups/{id}/driver-status — 차량 위치·ETA(동승자 웹에 실시간 표시) */
  suspend fun reportDriver(pickupId: String, request: DriverStatusRequest)

  /** POST /pickups/{id}/notifications — ETA_CHANGED · ARRIVING_SOON 문자 발송 */
  suspend fun sendNotification(pickupId: String, request: NotificationRequest)

  /** POST /pickups/{id}/arrival — 정차, 동승자 웹에 같은 1분 대기 타이머 */
  suspend fun reportArrival(pickupId: String, request: ArrivalRequest)

  /** POST /pickups/{id}/complete — 탑승 완료: 위치 공유 종료·링크 만료·기록 저장 */
  suspend fun completePickup(pickupId: String, request: CompletePickupRequest)

  /** GET /pickups/recent?limit= — 최근 픽업(같은 사람·같은 위치 다시 불러오기) */
  suspend fun recentPickups(limit: Int): List<PickupRecordDto>
}
