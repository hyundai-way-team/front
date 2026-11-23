package com.kscho.way.domain.usecase.restriction

import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupZones
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.repository.restriction.StopRestrictionRepository
import javax.inject.Inject

class GetPickupZonesUseCase @Inject constructor(
  private val stopRestrictionRepository: StopRestrictionRepository,
) {
  suspend operator fun invoke(pickup: LatLng): List<RestrictedZone> =
    PickupZones.around(pickup, stopRestrictionRepository.zonesNear(pickup, PickupZones.RADIUS_METERS))
}
