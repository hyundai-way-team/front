package com.kscho.way.data.dataSource.remote

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.response.StopZoneResponse
import com.kscho.way.domain.model.base.ApiState

interface StopZoneRemoteDataSource {
  suspend fun getStopZones(center: LatLngDto, radiusMeters: Int): ApiState<List<StopZoneResponse>>
}
