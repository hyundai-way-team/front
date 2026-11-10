package com.kscho.way.server

import com.kscho.way.data.mock.MockWayApi
import com.kscho.way.server.pickup.PickupHub
import com.kscho.way.server.sms.OutboxTextLog
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * 임시 로컬 서버.
 *
 *   ./gradlew :server:run
 *
 * - 차가 보낸 문자 사본(동승자 휴대폰 흉내): http://localhost:8080/dev/sms
 * - 에뮬레이터 앱 → 서버: http://10.0.2.2:8080 (에뮬레이터에서 본 이 컴퓨터)
 * - 같은 와이파이의 휴대폰으로 열려면 WAY_HOST=0.0.0.0 WAY_PUBLIC_URL=http://<이 컴퓨터 IP>:8080 으로 띄운다
 *   (단, 브라우저는 http 주소에선 위치 권한을 막는다 — 실제 위치 공유는 localhost 나 https 에서만)
 */
fun main() {
  val port = setting("way.port", "WAY_PORT")?.toIntOrNull() ?: 8080
  val host = setting("way.host", "WAY_HOST") ?: "127.0.0.1"
  val publicUrl = setting("way.publicUrl", "WAY_PUBLIC_URL") ?: "http://localhost:$port"
  val mapDir = setting("way.mapDir", "WAY_MAP_DIR")?.let(::File)?.takeIf { it.isDirectory }
  val webDir = setting("way.webDir", "WAY_WEB_DIR")?.let(::File)?.takeIf { it.isDirectory }

  val deps = createDeps(publicUrl, mapDir, webDir = webDir, log = ::println)
  println(
    """
    |
    |  WAY 로컬 서버  http://localhost:$port  (bind $host)
    |  · 문자 사본     http://localhost:$port/dev/sms  (문자는 차가 운전자 번호로 보낸다)
    |  · 에뮬레이터    http://10.0.2.2:$port
    |  · 참여 링크     $publicUrl/p/…
    |  · 지도 파일     ${mapDir ?: "없음 — way.mapDir 를 앱 assets/map 으로"}
    |
    """
      .trimMargin()
  )
  embeddedServer(CIO, port = port, host = host) { wayModule(deps) }.start(wait = true)
}

fun createDeps(
  publicUrl: String,
  mapDir: File?,
  webDir: File? = null,
  clock: () -> Long = System::currentTimeMillis,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
  log: (String) -> Unit = {},
): ServerDeps {
  // 시연 동네 데이터(장소 검색·역지오코딩·정차 금지 구간·운전자 프로필·지난 픽업)는 앱 mock 과 같은 것을 쓴다
  val places = MockWayApi(scope, clock)
  val outbox = OutboxTextLog(clock, log = { log("[문자 사본] $it") })
  val hub = PickupHub(clock, outbox, publicUrl, seedHistory = runBlocking { places.recentPickups(20) })
  return ServerDeps(hub, places, outbox, mapDir, webDir)
}

private fun setting(property: String, env: String): String? = System.getProperty(property) ?: System.getenv(env)
