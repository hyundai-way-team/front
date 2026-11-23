package com.kscho.way.data.dataSource.remote

import com.kscho.way.data.api.WayNetwork
import com.kscho.way.data.dataSource.base.NETWORK_ERROR_CODE
import com.kscho.way.data.dataSource.base.RESPONSE_MAPPING_ERROR_CODE
import com.kscho.way.data.dto.LatLngDto
import com.kscho.way.data.request.ArrivalRequest
import com.kscho.way.data.request.CreatePickupRequest
import com.kscho.way.data.request.DriverStatusRequest
import com.kscho.way.data.response.CreatePickupResponse
import com.kscho.way.domain.model.base.ApiState
import com.kscho.way.domain.model.base.FailState
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PickupRemoteDataSourceImplTest {
  private val server = MockWebServer()
  private lateinit var dataSource: PickupRemoteDataSourceImpl

  private val request =
    CreatePickupRequest(
      passengerName = "이서연",
      passengerCallName = "서연",
      pickupPoint = LatLngDto(37.5571, 126.942),
      pickupTitle = "골목 입구 앞",
      pickupDetail = "신촌로 · 도로 북쪽 인도",
      arrivalAtEpochMillis = 1_000L,
      vehicleDescription = "흰색 아반떼",
      plateNumber = "12가 3456",
      driverName = "김도윤",
      driverCallName = "도윤",
    )

  @Before
  fun setUp() {
    server.start()
    dataSource = PickupRemoteDataSourceImpl(WayNetwork.api(server.url("/").toString()))
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun createPickup_postsRequestAndReadsResponse() = runBlocking {
    server.enqueue(MockResponse().setBody("""{"pickupId":"p1","shareUrl":"https://way.test/p/t1","unknown":1}"""))

    val result = dataSource.createPickup(request)

    assertEquals(ApiState.Success(CreatePickupResponse("p1", "https://way.test/p/t1")), result)
    val recorded = server.takeRequest()
    assertEquals("POST", recorded.method)
    assertEquals("/api/pickups", recorded.path)
    assertTrue(recorded.body.readUtf8().contains("\"passengerName\":\"이서연\""))
  }

  @Test
  fun reportDriver_emptyResponse_isSuccess() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(200))

    val result = dataSource.reportDriver("p1", DriverStatusRequest(null, 600, 1800, 1_000L))

    assertEquals(ApiState.Success(Unit), result)
    assertEquals("/api/pickups/p1/driver-status", server.takeRequest().path)
  }

  @Test
  fun serverError_keepsStatusCodeAndBody() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(404).setBody("픽업이 없어요"))

    val result = dataSource.reportArrival("nope", ArrivalRequest(0, 60))

    assertEquals(ApiState.Fail(FailState(404, "픽업이 없어요")), result)
  }

  @Test
  fun brokenBody_isMappingFailure() = runBlocking {
    server.enqueue(MockResponse().setBody("{ 깨진"))

    val result = dataSource.createPickup(request)

    assertEquals(RESPONSE_MAPPING_ERROR_CODE, (result as ApiState.Fail).failState.code)
  }

  @Test
  fun droppedConnection_isNetworkFailure() = runBlocking {
    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

    val result = dataSource.createPickup(request)

    assertEquals(NETWORK_ERROR_CODE, (result as ApiState.Fail).failState.code)
  }
}
