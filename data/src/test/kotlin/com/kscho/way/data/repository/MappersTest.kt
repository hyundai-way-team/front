package com.kscho.way.data.repository

import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.PassengerStatusDto
import com.kscho.way.data.remote.SharingWire
import com.kscho.way.data.remote.WalkRouteDto
import com.kscho.way.domain.model.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
  @Test
  fun passengerWalk_reachesTheDriverScreen() {
    // 서버가 찾은 걷는 길(동승자 웹 P-05 와 같은 것) — 운전자 D-08 '도보 N분 거리'와 지도 경로
    val dto =
      PassengerStatusDto(
        linkOpened = true,
        sharing = SharingWire.SHARING,
        location = LatLngDto(37.5577, 126.942),
        walk = WalkRouteDto(listOf(LatLngDto(37.5577, 126.942), LatLngDto(37.5567, 126.942)), meters = 120, seconds = 107),
      )
    val walk = dto.toDomain().walk!!
    assertEquals(listOf(LatLng(37.5577, 126.942), LatLng(37.5567, 126.942)), walk.path)
    assertEquals(120, walk.meters)
    assertEquals(107, walk.seconds)
  }

  @Test
  fun noWalkYet_staysNull() {
    assertNull(PassengerStatusDto(sharing = SharingWire.SHARING).toDomain().walk)
  }
}
