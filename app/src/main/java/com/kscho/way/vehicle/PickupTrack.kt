package com.kscho.way.vehicle

import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.route.NavEvent
import kotlin.math.roundToInt

/**
 * 픽업 위치까지 — 내비 안내를 걸지 않고 Way 가 직접 남은 시간·거리·도착을 어림한다.
 *
 * Pleos 내비는 경로를 요청(requestRoute)하면 화면 앞으로 나와 Way 를 가린다. 운전자가 픽업까지는 Way 화면(D-06~D-09)을 보고
 * 탑승한 뒤에야 내비로 넘어가야 하므로, 픽업 구간은 화면을 띄우지 않는 길 계산(requestTrip)과 차 위치 묻기만 쓴다.
 * 길 계산은 [TRIP_REFRESH_MILLIS] 마다 다시 하고, 그 사이에는 그때보다 픽업 위치에 얼마나 가까워졌는지(직선 거리 비)로 줄인다.
 */
internal object PickupTrack {
  /** 차 위치 묻는 간격. */
  const val POLL_MILLIS = 3_000L
  /** 내비에 남은 길을 다시 계산해 달라는 간격. */
  const val TRIP_REFRESH_MILLIS = 15_000L
  /** 픽업 위치 이만큼 안이면 도착(내비의 도착 알림 대신). */
  const val ARRIVE_METERS = 35.0

  /** 내비 길 계산 답 한 번 — [from] 은 그때 차 위치(없으면 비율 보정 없이). */
  data class Trip(val meters: Int, val seconds: Int, val from: LatLng?)

  /** 아무것도 모르면(위치도 길 계산도 없음) null — '남은 0초'로 잘못 알리지 않게. */
  fun progress(here: LatLng?, destination: LatLng, trip: Trip?, now: Long): NavEvent.Progress? {
    if (here == null && trip == null) return null
    val (meters, seconds) =
      when {
        trip != null -> {
          val ratio =
            if (here != null && trip.from != null) {
              val then = Geo.distanceMeters(trip.from, destination).coerceAtLeast(1.0)
              (Geo.distanceMeters(here, destination) / then).coerceIn(0.0, 1.5)
            } else {
              1.0
            }
          (trip.meters * ratio).roundToInt() to (trip.seconds * ratio).roundToInt()
        }
        else -> {
          // 내비가 답하지 않으면 직선거리로 어림(도심 25km/h, 우회 1.3배) — preview 와 같은 규칙
          val m = Geo.distanceMeters(here!!, destination) * 1.3
          m.roundToInt() to (m / 7.0).roundToInt()
        }
      }
    return NavEvent.Progress(here, seconds, meters, now + seconds * 1000L)
  }

  fun arrived(here: LatLng?, destination: LatLng): Boolean = here != null && Geo.distanceMeters(here, destination) <= ARRIVE_METERS
}
