package com.kscho.way.data.mapper

import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.response.PassengerStatusResponse
import com.kscho.way.data.response.SharingWire
import com.kscho.way.data.response.WalkRouteResponse
import com.kscho.way.domain.model.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
  @Test
  fun passengerWalk_reachesTheDriverScreen() {
    val dto =
      PassengerStatusResponse(
        linkOpened = true,
        sharing = SharingWire.SHARING,
        location = LatLngDto(37.5577, 126.942),
        walk = WalkRouteResponse(listOf(LatLngDto(37.5577, 126.942), LatLngDto(37.5567, 126.942)), meters = 120, seconds = 107),
      )
    val walk = dto.toDomain().walk!!
    assertEquals(listOf(LatLng(37.5577, 126.942), LatLng(37.5567, 126.942)), walk.path)
    assertEquals(120, walk.meters)
    assertEquals(107, walk.seconds)
  }

  @Test
  fun noWalkYet_staysNull() {
    assertNull(PassengerStatusResponse(sharing = SharingWire.SHARING).toDomain().walk)
  }
}
