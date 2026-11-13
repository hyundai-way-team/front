package com.kscho.way.server.sms

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 시연용 — 출발 문자의 참여 링크를 동승자 휴대폰 대신 **이 맥 브라우저로 바로 연다**(WAY_DEV_OPEN).
 *
 * 사람들 앞에서 운전자 앱(에뮬레이터)과 동승자 웹(맥 브라우저)을 한 화면에 나란히 보여 줄 때 쓴다.
 * 링크가 localhost 주소라 브라우저가 안전한 주소로 보고 위치 권한을 물을 수 있다 — 맥 위치로 실제 위치 공유가 된다.
 * 픽업마다 처음 온 링크(출발 문자)만 연다 — ETA 변경·곧 도착 문자는 같은 링크라 탭을 또 열지 않는다.
 */
class MacBrowserOpener(
  /** 열 브라우저 앱 이름(예: "Google Chrome"). null 이면 기본 브라우저 */
  private val app: String? = null,
  private val log: (String) -> Unit = {},
  /** 명령 실행 → 종료 코드. 테스트에서 바꿔 끼운다. */
  private val run: (List<String>) -> Int = ::exec,
) {
  private val opened = ConcurrentHashMap.newKeySet<String>()
  private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "way-browser-opener").apply { isDaemon = true } }

  fun onText(text: LoggedText) {
    val link = LINK.find(text.text)?.value?.trimEnd('.', ',', '!', '?', ')') ?: return
    if (!opened.add(text.pickupId)) return
    val command = listOfNotNull("open", app?.let { "-a" }, app, link)
    worker.execute {
      val code = runCatching { run(command) }.getOrElse { -1 }
      log(if (code == 0) "${app ?: "기본 브라우저"}로 열었어요 → $link" else "브라우저 열기 실패($code) → $link")
    }
  }

  /** 열기 대기 중인 것까지 마친다(테스트·종료용). */
  fun drain(timeoutMillis: Long = 5_000) {
    worker.shutdown()
    worker.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)
  }

  companion object {
    private val LINK = Regex("""https?://\S+""")

    /** WAY_DEV_OPEN 값 → 브라우저 앱 이름. 1·true 는 기본 브라우저(null). */
    fun appOf(setting: String): String? = setting.trim().takeUnless { it == "1" || it.equals("true", ignoreCase = true) || it.isEmpty() }

    private fun exec(command: List<String>): Int {
      val process = ProcessBuilder(command).redirectErrorStream(true).start()
      val output = process.inputStream.bufferedReader().readText()
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroy()
        return -1
      }
      if (process.exitValue() != 0) System.err.println("open: $output")
      return process.exitValue()
    }
  }
}
