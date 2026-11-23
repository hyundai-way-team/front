package com.kscho.way.domain.usecase.restriction

import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.repository.restriction.StopRestrictionRepository
import javax.inject.Inject

class GetStopZonesUseCase @Inject constructor(
  private val stopRestrictionRepository: StopRestrictionRepository,
) {
  suspend operator fun invoke(center: LatLng, radiusMeters: Int): List<RestrictedZone> =
    stopRestrictionRepository.zonesNear(center, radiusMeters)
}
