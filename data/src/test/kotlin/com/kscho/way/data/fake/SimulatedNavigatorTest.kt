package com.kscho.way.data.fake

import com.kscho.way.domain.model.route.NavEvent
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
    val preview = nav.preview(FakeWorld.VILLA_ALLEY_ENTRANCE, "픽업")
    val events = mutableListOf<NavEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { nav.events.toList(events) }

    nav.startGuidance(FakeWorld.VILLA_ALLEY_ENTRANCE, "픽업", "")
    advanceTimeBy(120_000)

    val progress = events.filterIsInstance<NavEvent.Progress>()
    assertTrue(events.last() is NavEvent.Arrived)
    assertEquals(0, progress.last().remainingMeters)

    val firstEta = progress.first().arrivalAtMillis
    assertTrue(abs(firstEta - preview.durationSeconds * 1000L) <= 1_000L)
    val lastEta = progress.last().arrivalAtMillis
    assertTrue(abs(lastEta - firstEta - 4 * 60_000L) <= 1_000L)

    val realSeconds = (events.last() as NavEvent.Arrived).atMillis / 1000
    assertTrue("실제 걸린 시간 ${realSeconds}초", realSeconds < (preview.durationSeconds + 240) / 20 + 2)
  }

  @Test
  fun route_followsSinchonRoFromCarStart() = runTest {
    val nav = SimulatedNavigator(backgroundScope, clock = { testScheduler.currentTime })
    val route = nav.preview(FakeWorld.VILLA_ALLEY_ENTRANCE, "픽업")
    assertTrue(route.path.size > 5)
    assertEquals(FakeWorld.CAR_START, route.path.first())
    assertEquals(FakeWorld.VILLA_ALLEY_ENTRANCE, route.path.last())
    route.path.drop(1).dropLast(1).forEach { assertTrue(FakeWorld.SINCHON_RO_NORTH.contains(it)) }
    assertTrue("${route.distanceMeters}m", route.distanceMeters in 1_600..2_000)
    assertTrue("${route.durationSeconds}s", route.durationSeconds > 6 * 60)
  }
}
