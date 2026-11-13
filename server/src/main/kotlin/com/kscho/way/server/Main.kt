package com.kscho.way.server

import com.kscho.way.data.mock.MockWayApi
import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.server.pickup.PickupHub
import com.kscho.way.server.sms.MacBrowserOpener
import com.kscho.way.server.sms.MacMessagesRelay
import com.kscho.way.server.sms.OutboxTextLog
import com.kscho.way.server.tls.LocalTls
import com.kscho.way.server.walk.OsmWalkRoutes
import com.kscho.way.server.walk.WalkAnswer
import io.ktor.server.cio.CIO
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 임시 로컬 서버.
 *
 *   ./gradlew :server:run
 *
 * - 차가 보낸 문자 사본(동승자 휴대폰 흉내): http://localhost:8080/dev/sms
 * - 에뮬레이터 앱 → 서버: http://10.0.2.2:8080 (에뮬레이터에서 본 이 컴퓨터)
 * - 같은 네트워크의 휴대폰으로 열려면 WAY_HOST=:: WAY_PUBLIC_URL=http://<이 맥 이름>.local:8080 으로 띄운다
 *   (단, 브라우저는 http 주소에선 위치 권한을 막는다 — 실제 위치 공유는 localhost 나 https 에서만)
 * - 휴대폰 실제 위치 공유(https): tools/dev/make_local_https.sh 로 인증서를 만들고 WAY_TLS_DIR=~/.way-dev-tls 로 띄운다.
 *   그러면 https 를 WAY_TLS_PORT(기본 8443)에 더 연다(http 8080 은 에뮬레이터용으로 그대로). WAY_PUBLIC_URL=https://<이 맥 이름>.local:8443
 *   아이폰은 사파리로 http://<이 맥 이름>.local:8080/dev/ca.mobileconfig 를 열어 프로파일 설치 → 인증서 신뢰 설정 켜기(외부 서비스 없음).
 * - 시연용 실제 문자: WAY_DEV_SMS_TO=<받을 휴대폰 번호> — 문자 사본을 이 맥의 '메시지' 앱으로 보낸다
 *   (기본 iMessage, WAY_DEV_SMS_VIA=sms 면 아이폰 문자 메시지 전달). 번호는 실행 설정에만 둔다(저장소에 넣지 않는다).
 * - 운영: WAY_DEV_ROUTES=0 — 개발용 경로(/dev/sms 문자 사본 등)를 끈다. 상태 확인은 /api/health.
 * - 시연용 맥에서 동승자 화면: WAY_DEV_OPEN=1 — 출발 문자의 링크를 이 맥 기본 브라우저로 바로 연다(앱 이름을 주면 그 브라우저, 예: Google Chrome).
 *   링크가 localhost 라 맥 브라우저에선 실제 위치 공유도 된다.
 */
fun main() {
  val port = setting("way.port", "WAY_PORT")?.toIntOrNull() ?: 8080
  val host = setting("way.host", "WAY_HOST") ?: "127.0.0.1"
  val publicUrl = setting("way.publicUrl", "WAY_PUBLIC_URL") ?: "http://localhost:$port"
  val mapDir = setting("way.mapDir", "WAY_MAP_DIR")?.let(::File)?.takeIf { it.isDirectory }
  val webDir = setting("way.webDir", "WAY_WEB_DIR")?.let(::File)?.takeIf { it.isDirectory }
  val tls = setting("way.tlsDir", "WAY_TLS_DIR")?.takeIf { it.isNotBlank() }?.let { LocalTls.load(File(it.replaceFirst("~", System.getProperty("user.home")))) }
  val tlsPort = setting("way.tlsPort", "WAY_TLS_PORT")?.toIntOrNull() ?: 8443
  val relay = setting("way.devSmsTo", "WAY_DEV_SMS_TO")?.takeIf { it.isNotBlank() }?.let {
      MacMessagesRelay(it, via = MacMessagesRelay.Via.of(setting("way.devSmsVia", "WAY_DEV_SMS_VIA")), log = { m -> println("[메시지 앱] $m") })
    }
  val opener = setting("way.devOpen", "WAY_DEV_OPEN")?.takeIf { it.isNotBlank() }?.let {
      MacBrowserOpener(MacBrowserOpener.appOf(it), log = { m -> println("[브라우저] $m") })
    }

  val devRoutes = setting("way.devRoutes", "WAY_DEV_ROUTES")?.trim() != "0"
  val deps =
    createDeps(
      publicUrl,
      mapDir,
      webDir = webDir,
      log = ::println,
      relay = relay,
      opener = opener,
      caCert = tls?.caCert,
      devRoutes = devRoutes,
      walkRoute = OsmWalkRoutes()::route,
    )
  println(
    """
    |
    |  WAY 로컬 서버  http://localhost:$port  (bind $host)
    |  · 문자 사본     ${if (devRoutes) "http://localhost:$port/dev/sms  (문자는 차가 운전자 번호로 보낸다)" else "끔 — 운영(WAY_DEV_ROUTES=0)"}
    |  · 에뮬레이터    http://10.0.2.2:$port
    |  · 참여 링크     $publicUrl/p/…
    |  · 지도 파일     ${mapDir ?: "없음 — way.mapDir 를 앱 assets/map 으로"}
    |  · https         ${tls?.let { "https://localhost:$tlsPort (아이폰 인증서: http://<이 맥 이름>.local:$port/dev/ca.mobileconfig)" } ?: "끔 — WAY_TLS_DIR 로 켠다"}
    |  · 실제 문자     ${relay?.let { "메시지 앱 → ${it.maskedNumber} (시연용)" } ?: "끔 — WAY_DEV_SMS_TO 로 켠다"}
    |  · 맥에서 열기   ${opener?.let { "출발 문자 링크를 이 맥 브라우저로 (시연용)" } ?: "끔 — WAY_DEV_OPEN=1 로 켠다"}
    |
    """
      .trimMargin()
  )
  if (tls == null) {
    embeddedServer(CIO, port = port, host = host) { wayModule(deps) }.start(wait = true)
  } else {
    // CIO 는 서버 TLS 를 못 연다 — https 를 켤 때만 Netty 로 http·https 둘 다
    embeddedServer(
        Netty,
        configure = {
          connector {
            this.port = port
            this.host = host
          }
          sslConnector(tls.keyStore, LocalTls.KEY_ALIAS, { tls.password }, { tls.password }) {
            this.port = tlsPort
            this.host = host
          }
        },
        module = { wayModule(deps) },
      )
      .start(wait = true)
  }
}

fun createDeps(
  publicUrl: String,
  mapDir: File?,
  webDir: File? = null,
  clock: () -> Long = System::currentTimeMillis,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
  log: (String) -> Unit = {},
  /** 시연용: 문자 사본을 맥 메시지 앱으로 실제 휴대폰에 보낸다 */
  relay: MacMessagesRelay? = null,
  /** 시연용: 출발 문자 링크를 이 맥 브라우저로 연다 */
  opener: MacBrowserOpener? = null,
  /** 시연용 로컬 https 의 CA 공개 인증서(/dev/ca.cer) */
  caCert: File? = null,
  /** 개발용 경로(/dev/sms 등). 운영에선 false */
  devRoutes: Boolean = true,
  /** 동승자 → 픽업 위치 걷는 길(walk.OsmWalkRoutes — 무료 공개 보행 경로 서버) */
  walkRoute: (suspend (LatLngDto, LatLngDto) -> WalkAnswer)? = null,
): ServerDeps {
  // 시연 동네 데이터(장소 검색·역지오코딩·정차 금지 구간)는 앱 mock 과 같은 것을 쓴다. 지난 픽업 기록은 앱에 있다
  val places = MockWayApi(scope, clock)
  val outbox = OutboxTextLog(clock, log = { log("[문자 사본] $it") }, onRecorded = {
      relay?.send(it.text)
      opener?.onText(it)
    })
  val hub = PickupHub(clock, outbox, publicUrl, walkRoute = walkRoute, scope = scope)
  return ServerDeps(hub, places, outbox, mapDir, webDir, caCert, devRoutes = devRoutes)
}

private fun setting(property: String, env: String): String? = System.getProperty(property) ?: System.getenv(env)
