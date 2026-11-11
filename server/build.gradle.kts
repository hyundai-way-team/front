// 임시 로컬 서버 — 운전자 앱(WayApi) · 동승자 웹 링크 · Pleos 문자 발송함.
// 실행: ./gradlew :server:run → http://localhost:8080 (에뮬레이터에서는 http://10.0.2.2:8080)
// 서버 담당 레포(way-server)가 생기면 API 모양(data/remote/WayApi.kt · Dtos.kt)만 맞추면 된다.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  application
}

kotlin {
  jvmToolchain(17)
}

application {
  mainClass.set("com.kscho.way.server.MainKt")
}

dependencies {
  // 앱과 같은 요청·응답 모양(Dtos) + 시연 동네 장소·정차 금지 구간(MockWorld)
  implementation(project(":data"))

  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  // 로컬 https(시연용, WAY_TLS_DIR) — CIO 는 서버 TLS 를 못 열어 그때만 Netty 로 띄운다
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.websockets)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.partial.content)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.server.call.logging)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.logback.classic)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.ktor.server.test.host)
}

// 지도 파일은 앱 assets 와 같은 것을 그대로 내보낸다(복사하지 않음). 실행 위치와 상관없이 찾도록 경로를 넘긴다.
tasks.named<JavaExec>("run") {
  systemProperty("way.mapDir", rootProject.file("app/src/main/assets/map").absolutePath)
  // 웹 파일은 소스 폴더에서 바로 — 고치고 새로고침
  systemProperty("way.webDir", project.file("src/main/resources/web").absolutePath)
  System.getenv("WAY_PUBLIC_URL")?.let { systemProperty("way.publicUrl", it) }
}

tasks.withType<Test>().configureEach {
  systemProperty("way.mapDir", rootProject.file("app/src/main/assets/map").absolutePath)
}
