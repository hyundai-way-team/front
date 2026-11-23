package com.kscho.way.data.dataSource.remote

import com.kscho.way.data.api.WayNetwork
import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.domain.model.base.ApiState
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StopZoneRemoteDataSourceImplTest {
  private val server = MockWebServer()

  @Before
  fun setUp() {
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun getStopZones_sendsCenterAndRadius() = runBlocking {
    server.enqueue(
      MockResponse().setBody("""[{"id":"bus-1","name":"버스정류장","rule":"상시","path":[{"lat":37.55,"lng":126.94}]}]""")
    )
    val dataSource = StopZoneRemoteDataSourceImpl(WayNetwork.api(server.url("/").toString()))

    val result = dataSource.getStopZones(LatLngDto(37.55, 126.94), 300)

    val zones = (result as ApiState.Success).data
    assertEquals("bus-1", zones.single().id)
    assertEquals(10.0, zones.single().radiusMeters, 0.0)
    assertEquals("/api/stop-zones?lat=37.55&lng=126.94&radius=300", server.takeRequest().path)
  }
}
