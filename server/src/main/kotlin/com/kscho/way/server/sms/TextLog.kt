package com.kscho.way.server.sms

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/**
 * 동승자 문자 기록. **문자는 서버가 아니라 차가 운전자 번호로 보낸다**(Pleos 문자 · 차에 연결된 휴대폰).
 * 서버는 같은 문구(PickupTexts)를 사본으로만 남긴다 — 휴대폰을 연결할 수 없는 에뮬레이터 시연에서 링크를 열고, 기록으로 쓴다.
 */
fun interface TextLog {
  /** [to] 는 받는 사람 이름 — 서버는 동승자 번호를 받지 않는다. */
  fun record(pickupId: String, to: String, text: String)
}

@Serializable
data class LoggedText(val id: Long, val pickupId: String, val to: String, val text: String, val at: Long)

/** 로컬 서버용 사본함 — /dev/sms 화면(동승자 휴대폰 문자 앱 흉내)에서 보고 링크를 연다. */
class OutboxTextLog(
  private val clock: () -> Long = System::currentTimeMillis,
  private val log: (String) -> Unit = {},
) : TextLog {
  private val seq = AtomicLong(0)
  private val _messages = MutableStateFlow<List<LoggedText>>(emptyList())
  val messages: StateFlow<List<LoggedText>> = _messages.asStateFlow()

  override fun record(pickupId: String, to: String, text: String) {
    val entry = LoggedText(seq.incrementAndGet(), pickupId, to, text, clock())
    _messages.update { it + entry }
    log("차 → $to (사본)\n$text")
  }
}
