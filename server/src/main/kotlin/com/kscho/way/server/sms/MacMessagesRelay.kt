package com.kscho.way.server.sms

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 시연용 — 차가 보낼 문자를 이 맥의 '메시지' 앱으로 실제 휴대폰에 보낸다.
 *
 * 실제로는 차가 블루투스로 연결된 운전자 휴대폰으로 보낸다. 에뮬레이터엔 연결된 휴대폰이 없어 Pleos 문자가 나가지 않으므로,
 * 아이폰 '문자 메시지 전달'이 켜진 맥에서 로컬 서버를 띄울 때만 쓴다(WAY_DEV_SMS_TO).
 * 받는 번호는 서버 설정에만 둔다 — API 로는 동승자 번호를 받지 않는다.
 *
 * 보내는 길([via]): 기본은 iMessage — 자기 번호로 보내는 문자(SMS)는 아이폰 문자 전달을 거치며 실패했다(11/11).
 * iMessage 는 같은 Apple ID 의 아이폰 메시지 앱에 바로 뜬다. 다른 사람 번호로 보낼 땐 SMS 로 바꿀 수 있다(WAY_DEV_SMS_VIA=sms).
 *
 * 문자 내용·번호는 AppleScript 인자로 넘긴다(스크립트 글자에 끼워 넣지 않는다).
 * 보내는 데 1초쯤 걸려 따로 도는 스레드 하나에서 순서대로 보낸다.
 */
class MacMessagesRelay(
  /** 받을 휴대폰 번호(숫자만 남긴다) */
  to: String,
  private val via: Via = Via.IMESSAGE,
  private val log: (String) -> Unit = {},
  /** osascript 실행 — (스크립트, 인자) → 종료 코드. 테스트에서 바꿔 끼운다. */
  private val runScript: (script: String, args: List<String>) -> Int = ::osascript,
) {
  private val number = to.filter(Char::isDigit)
  private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "way-messages-relay").apply { isDaemon = true } }

  init {
    require(number.length in 9..12) { "WAY_DEV_SMS_TO 번호 모양이 이상해요" }
  }

  /** 가린 번호 — 로그·안내용 */
  val maskedNumber: String
    get() = "${number.take(3)}-****-${number.takeLast(4)}"

  fun send(text: String) {
    worker.execute {
      val code = runCatching { runScript(SCRIPT, listOf(text, number, via.serviceType)) }.getOrElse { -1 }
      log(if (code == 0) "메시지 앱(${via.serviceType})으로 보냄 → $maskedNumber" else "메시지 앱(${via.serviceType}) 보내기 실패($code) → $maskedNumber")
    }
  }

  /** 보내기 대기 중인 것까지 마친다(테스트·종료용). */
  fun drain(timeoutMillis: Long = 5_000) {
    worker.shutdown()
    worker.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)
  }

  /** 메시지 앱 계정 종류(AppleScript service type 글자). */
  enum class Via(val serviceType: String) {
    /** 같은 Apple ID 기기로 바로 — 자기 번호로 보낼 때 */
    IMESSAGE("iMessage"),
    /** 아이폰 '문자 메시지 전달'을 거친 문자 */
    SMS("SMS");

    companion object {
      fun of(text: String?): Via = if (text.equals("sms", ignoreCase = true)) SMS else IMESSAGE
    }
  }

  companion object {
    /** argv = 내용 · 번호 · 계정 종류("iMessage" | "SMS"). 그 종류의 켜진 계정이 없으면 실패. */
    val SCRIPT =
      """
      on run argv
        set theText to item 1 of argv
        set theNumber to item 2 of argv
        set wanted to item 3 of argv
        tell application "Messages"
          set chosen to missing value
          repeat with a in accounts
            try
              if ((service type of a) as text) is wanted and enabled of a then
                set chosen to a
                exit repeat
              end if
            end try
          end repeat
          if chosen is missing value then error wanted & " 계정이 없어요 — 메시지 앱 설정을 확인해 주세요"
          send theText to participant theNumber of chosen
        end tell
      end run
      """
        .trimIndent()

    private fun osascript(script: String, args: List<String>): Int {
      val process = ProcessBuilder(listOf("osascript", "-") + args).redirectErrorStream(true).start()
      process.outputStream.bufferedWriter().use { it.write(script) }
      val output = process.inputStream.bufferedReader().readText()
      if (!process.waitFor(30, TimeUnit.SECONDS)) {
        process.destroy()
        return -1
      }
      if (process.exitValue() != 0) System.err.println("osascript: $output")
      return process.exitValue()
    }
  }
}
