package com.kscho.way.data.dataSource.remote

import com.kscho.way.data.api.WayApi
import com.kscho.way.data.dataSource.base.apiCall
import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.response.StopZoneResponse
import com.kscho.way.domain.model.base.ApiState
import javax.inject.Inject

class StopZoneRemoteDataSourceImpl @Inject constructor(
  private val wayApi: WayApi,
) : StopZoneRemoteDataSource {
  override suspend fun getStopZones(center: LatLngDto, radiusMeters: Int): ApiState<List<StopZoneResponse>> =
    apiCall { wayApi.getStopZones(center.lat, center.lng, radiusMeters) }
}
