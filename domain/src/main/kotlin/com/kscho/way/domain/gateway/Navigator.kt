package com.kscho.way.domain.gateway

import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.route.NavEvent
import com.kscho.way.domain.model.route.RoutePreview
import kotlinx.coroutines.flow.SharedFlow

interface Navigator {
  suspend fun currentLocation(): LatLng?

  suspend fun preview(destination: LatLng, name: String): RoutePreview

  suspend fun startGuidance(destination: LatLng, name: String, address: String)

  val events: SharedFlow<NavEvent>

  fun stopGuidance()

  suspend fun handOver(destination: LatLng, name: String, address: String)

  fun openNavi() {}
}
