package com.kscho.way.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSessionTest {
  @Test
  fun newsBeforeTheRequestedRouteStarts_isIgnored() {
    // 요청 직후 Pleos 는 지난 경로(목적지 안내)의 '남은 0초' 를 한 번 더 보낸다 — 출발하자마자 '곧 도착' 문자가 나가던 원인
    val route = RouteSession()
    route.requested()
    assertFalse(route.live)
    assertFalse(route.arrive())

    route.started()
    assertTrue(route.live)
  }

  @Test
  fun arrival_isReportedOnce_andStopsNews() {
    val route = RouteSession()
    route.requested()
    route.started()
    assertTrue(route.arrive())
    assertFalse(route.arrive())
    assertFalse(route.live)
  }

  @Test
  fun routeStartedWithoutOurRequest_isIgnored() {
    val route = RouteSession()
    route.started() // 운전자가 내비에서 직접 건 안내 등
    assertFalse(route.live)
  }

  @Test
  fun stopped_dropsNewsUntilNextRequestStarts() {
    val route = RouteSession()
    route.requested()
    route.started()
    route.stopped()
    assertFalse(route.live)
    route.requested()
    assertFalse(route.live) // 다음 요청도 그 경로가 시작돼야
    route.started()
    assertTrue(route.live)
  }
}
