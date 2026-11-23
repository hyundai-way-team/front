package com.kscho.way.data.repository.restriction

import com.kscho.way.data.dataSource.remote.StopZoneRemoteDataSource
import com.kscho.way.data.mapper.toDomain
import com.kscho.way.data.mapper.toDto
import com.kscho.way.domain.model.base.ApiState
import com.kscho.way.domain.model.base.map
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.repository.restriction.StopRestrictionRepository
import javax.inject.Inject

class StopRestrictionRepositoryImpl @Inject constructor(
  private val stopZoneRemoteDataSource: StopZoneRemoteDataSource,
) : StopRestrictionRepository {
  override suspend fun zonesNear(location: LatLng, radiusMeters: Int): ApiState<List<RestrictedZone>> =
    stopZoneRemoteDataSource.getStopZones(location.toDto(), radiusMeters).map { zones -> zones.map { it.toDomain() } }
}
