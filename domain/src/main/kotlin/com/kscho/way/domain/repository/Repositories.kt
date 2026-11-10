package com.kscho.way.domain.repository

import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationDescription
import com.kscho.way.domain.model.MapFeatures
import com.kscho.way.domain.model.MyVehicle
import com.kscho.way.domain.model.PassengerStatus
import com.kscho.way.domain.model.PickupOutcome
import com.kscho.way.domain.model.PickupPlan
import com.kscho.way.domain.model.PickupRecord
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.StopRestriction
import kotlinx.coroutines.flow.Flow

/** 운전자 연락처(기획: Pleos 연락처 — 실제 연동 가능 여부 미정). */
interface ContactRepository {
  /** 이름 또는 전화번호로 찾는다. 빈 검색어면 전체. */
  suspend fun search(query: String): List<Contact>
}

interface PlaceRepository {
  /** 장소명·주소 둘 다로 검색(기획 3-2 수용 기준). */
  suspend fun search(query: String): List<Place>

  /** 핀 좌표를 사람이 알아보는 말로(역지오코딩). */
  suspend fun describe(location: LatLng): LocationDescription

  /** 지도에 그릴 주변 도로·장소·정차 금지 구간. */
  suspend fun mapFeatures(center: LatLng, radiusMeters: Int): MapFeatures
}

interface StopRestrictionRepository {
  /** 도착 예정 시각([arrivalAtMillis]) 기준으로 그 좌표가 정차 금지 구간인지. */
  suspend fun check(location: LatLng, arrivalAtMillis: Long): StopRestriction
}

interface VehicleProfileRepository {
  suspend fun myVehicle(): MyVehicle
}

data class CreatedPickup(val id: String, val shareUrl: String)

/**
 * 픽업 세션 — 서버가 동승자 웹 링크를 만들고 차와 동승자 웹을 실시간으로 잇는다.
 * 문자는 서버가 아니라 차가 운전자 번호로 보낸다([com.kscho.way.domain.messaging.PassengerMessenger]).
 * 운전자 앱은 상황을 알리고(보고), 동승자 상태를 받는다.
 */
interface PickupRepository {
  /** 세션을 만들고 참여 링크를 받는다(기획 3-4) — 문자는 이 링크로 차가 보낸다. */
  suspend fun create(plan: PickupPlan, arrivalAtMillis: Long): CreatedPickup

  /** 동승자 링크 확인·위치 공유·실시간 위치(기획 3-7). */
  fun passengerStatus(pickupId: String): Flow<PassengerStatus>

  /** 차량 위치·ETA — 동승자 웹이 실시간으로 보여 준다. */
  suspend fun reportDriver(pickupId: String, location: LatLng?, remainingSeconds: Int, remainingMeters: Int, arrivalAtMillis: Long)

  /** 도착 시간이 바뀌었다 — 동승자 웹에 "도착 시간이 바뀌었어요" 배너(기획 3-5). */
  suspend fun notifyEtaChanged(pickupId: String, previousArrivalAtMillis: Long, arrivalAtMillis: Long)

  /** 곧 도착 — 동승자 웹이 '곧 도착해요' 화면으로(기획 3-6). */
  suspend fun notifyArrivingSoon(pickupId: String, arrivalAtMillis: Long)

  /** 정차 — 동승자 웹에도 같은 대기 타이머를 띄운다(기획 3-9). */
  suspend fun reportArrived(pickupId: String, arrivedAtMillis: Long, waitSeconds: Int)

  /** 탑승 완료 — 위치 공유 종료·링크 만료·기록(기획 3-10, 3-11). */
  suspend fun complete(pickupId: String, outcome: PickupOutcome)
}

interface PickupHistoryRepository {
  /** 최근 픽업, 최근 것부터. */
  suspend fun recent(limit: Int): List<PickupRecord>
}
