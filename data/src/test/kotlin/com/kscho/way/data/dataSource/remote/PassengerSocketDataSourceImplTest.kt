package com.kscho.way.data.dataSource.remote

import com.kscho.way.data.api.WayNetwork
import com.kscho.way.data.response.SharingWire
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PassengerSocketDataSourceImplTest {
  private val server = MockWebServer()

  @Before
  fun setUp() {
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun dataSource() = PassengerSocketDataSourceImpl(server.url("/").toString(), WayNetwork.client(), WayNetwork.json, reconnectDelayMillis = 10)

  private fun socket(message: String, closeCode: Int) =
    MockResponse().withWebSocketUpgrade(
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          webSocket.send(message)
          webSocket.close(closeCode, null)
        }
      }
    )

  @Test
  fun pickupGone_endsTheFlow() = runBlocking {
    server.enqueue(socket("""{"linkOpened":true,"sharing":"SHARING"}""", 1008))

    val updates = withTimeout(5_000) { dataSource().passengerUpdates("p1").toList() }

    assertEquals(listOf(SharingWire.SHARING), updates.map { it.sharing })
    assertEquals("/api/pickups/p1/passenger", server.takeRequest().path)
  }

  @Test
  fun droppedSocket_reconnects() = runBlocking {
    server.enqueue(socket("""{"linkOpened":true}""", 1011))
    server.enqueue(socket("""{"linkOpened":true,"sharing":"DECLINED"}""", 1008))

    val updates = withTimeout(5_000) { dataSource().passengerUpdates("p1").take(2).toList() }

    assertEquals(listOf(SharingWire.NOT_YET, SharingWire.DECLINED), updates.map { it.sharing })
    assertEquals(2, server.requestCount)
  }
}
