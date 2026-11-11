package com.kscho.way.domain.usecase

import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.PickupPoint
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.model.RoutePreview
import com.kscho.way.domain.model.StopRestriction
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.repository.StopRestrictionRepository
import com.kscho.way.domain.vehicle.Navigator
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 핀을 놓은 곳을 확인한 결과(D-04 카드 · D-05 요약). */
data class PickupPointCheck(
  val point: PickupPoint,
  val route: RoutePreview,
  /** 확인한 시점 기준 도착 예정 시각 — 정차 규제는 이 시각으로 본다. */
  val arrivalAtMillis: Long,
  val restriction: StopRestriction,
) {
  /** 정차 금지 구간이면 확정하지 않는다(핀을 옮기게). */
  val canConfirm: Boolean
    get() = restriction !is StopRestriction.NoStopping
}

/**
 * 기획 3-2: 운전자가 맞춘 핀 → 위치 이름 · 약속 장소에서 거리 · 경로(ETA) · 도착 시각 기준 정차 규제.
 * 정차 금지 판단은 앱이 한다(서버는 근처 구간 데이터만).
 */
private const val ZONE_SEARCH_METERS = 100

class CheckPickupPoint(
  private val places: PlaceRepository,
  private val restrictions: StopRestrictionRepository,
  private val navigator: Navigator,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  suspend operator fun invoke(location: LatLng, place: Place?): PickupPointCheck = coroutineScope {
    val description = async { places.describe(location) }
    val route = navigator.preview(location, place?.name ?: "픽업 위치")
    val arrival = clock() + route.durationSeconds * 1000L
    val restriction = StopRestrictionRules.check(location, arrival, restrictions.zonesNear(location, ZONE_SEARCH_METERS))
    val distance = place?.let { Geo.distanceMeters(it.location, location).roundToInt() }
    PickupPointCheck(PickupPoint(location, description.await(), distance), route, arrival, restriction)
  }
}
