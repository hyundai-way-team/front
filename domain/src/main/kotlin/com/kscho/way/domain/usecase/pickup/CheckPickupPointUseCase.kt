package com.kscho.way.domain.usecase.pickup

import com.kscho.way.domain.gateway.Navigator
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupPoint
import com.kscho.way.domain.model.pickup.PickupPointCheck
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.pickup.StopRestrictionRules
import com.kscho.way.domain.repository.place.PlaceRepository
import com.kscho.way.domain.repository.restriction.StopRestrictionRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val ZONE_SEARCH_METERS = 100

class CheckPickupPointUseCase(
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
