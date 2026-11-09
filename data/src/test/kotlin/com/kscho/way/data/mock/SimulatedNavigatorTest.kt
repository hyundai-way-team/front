package com.kscho.way.data.mock

import com.kscho.way.domain.model.NavEvent
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedNavigatorTest {
  @Test
  fun drive_progressesWithTrafficDelayThenArrives() = runTest {
    val nav = SimulatedNavigator(backgroundScope, clock = { testScheduler.currentTime })
    val preview = nav.preview(MockWorld.VILLA_ALLEY_ENTRANCE, "픽업")
    val events = mutableListOf<NavEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { nav.events.toList(events) }

    nav.startGuidance(MockWorld.VILLA_ALLEY_ENTRANCE, "픽업", "")
    advanceTimeBy(120_000) // backgroundScope 작업은 advanceUntilIdle 이 기다리지 않는다

    val progress = events.filterIsInstance<NavEvent.Progress>()
    assertTrue(events.last() is NavEvent.Arrived)
    assertEquals(0, progress.last().remainingMeters)

    // 처음 ETA 는 미리보기와 같고(반올림 1초 이내), 정체를 만나면 4분 늦어진다
    val firstEta = progress.first().arrivalAtMillis
    assertTrue(abs(firstEta - preview.durationSeconds * 1000L) <= 1_000L)
    val lastEta = progress.last().arrivalAtMillis
    assertTrue(abs(lastEta - firstEta - 4 * 60_000L) <= 1_000L)

    // 실제로는 20배 빨리 끝난다(시연용)
    val realSeconds = (events.last() as NavEvent.Arrived).atMillis / 1000
    assertTrue("실제 걸린 시간 ${realSeconds}초", realSeconds < (preview.durationSeconds + 240) / 20 + 2)
  }

  @Test
  fun route_followsSinchonRoFromCarStart() = runTest {
    val nav = SimulatedNavigator(backgroundScope, clock = { testScheduler.currentTime })
    val route = nav.preview(MockWorld.VILLA_ALLEY_ENTRANCE, "픽업")
    // 출발 → 신촌로 북쪽 차로 꼭짓점들 → 픽업 위치 (ㄱ자 3점이 아니다)
    assertTrue(route.path.size > 5)
    assertEquals(MockWorld.CAR_START, route.path.first())
    assertEquals(MockWorld.VILLA_ALLEY_ENTRANCE, route.path.last())
    route.path.drop(1).dropLast(1).forEach { assertTrue(MockWorld.SINCHON_RO_NORTH.contains(it)) }
    // 약 1.8km, 4m/s 로 7분 넘게 — 5분 전 문자가 출발하자마자 나가지 않게
    assertTrue("${route.distanceMeters}m", route.distanceMeters in 1_600..2_000)
    assertTrue("${route.durationSeconds}s", route.durationSeconds > 6 * 60)
  }
}

