package com.kscho.way.server.walk

import com.kscho.way.data.dto.LatLngDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 걷는 길 — 무료 공개 보행 경로 서버(OpenStreetMap · OSRM foot, routing.openstreetmap.de) 응답을 읽는다. */
class OsmWalkRoutesTest {
  // 실제 응답에서 앞 점 4개만 남긴 것(신촌 · 2026-11-14)
  private val sample =
    """
    {"code": "Ok", "routes": [{"distance": 366.2, "duration": 293.2, "geometry": {"type": "LineString",
     "coordinates": [[126.940493, 37.558497], [126.940614, 37.558331], [126.940892, 37.558358], [126.941194, 37.558387]]},
     "weight": 35.55, "legs": []}], "waypoints": []}
    """.trimIndent()

  @Test
  fun readsDistanceTimeAndPath_lngLatToLatLng() {
    val route = OsmWalkRoutes.parse(sample)!!
    assertEquals(366, route.meters)
    assertEquals(293, route.seconds)
    assertEquals(LatLngDto(37.558497, 126.940493), route.path.first())
    assertEquals(4, route.path.size)
  }

  @Test
  fun noRoute_isAnAnswer_butBusyOrBrokenIsUnavailable() {
    // '길 없음'(기억해 되묻지 않음)과 '지금 못 물음'(잠시 뒤 다시)은 다르다
    assertEquals(WalkAnswer.NoRoute, OsmWalkRoutes.answerOf(200, """{"code":"NoRoute","routes":[]}"""))
    assertEquals(WalkAnswer.NoRoute, OsmWalkRoutes.answerOf(400, """{"code":"NoSegment","message":"Could not find a matching segment"}"""))
    assertEquals(WalkAnswer.Unavailable, OsmWalkRoutes.answerOf(429, "Too Many Requests"))
    assertEquals(WalkAnswer.Unavailable, OsmWalkRoutes.answerOf(503, "<html>busy</html>"))
    assertEquals(WalkAnswer.Unavailable, OsmWalkRoutes.answerOf(200, "<html>busy</html>"))
    assertTrue(OsmWalkRoutes.answerOf(200, sample) is WalkAnswer.Found)
  }

  @Test
  fun asksWithLngLatOrder() {
    val url = OsmWalkRoutes.url("https://routing.openstreetmap.de/routed-foot", LatLngDto(37.5585, 126.9405), LatLngDto(37.5567, 126.942))
    assertTrue(url, url.startsWith("https://routing.openstreetmap.de/routed-foot/route/v1/driving/126.9405,37.5585;126.942,37.5567?"))
    assertTrue(url.contains("geometries=geojson") && url.contains("overview=full"))
  }
}
