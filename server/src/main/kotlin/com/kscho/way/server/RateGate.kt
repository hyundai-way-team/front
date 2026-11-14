package com.kscho.way.server

/**
 * 열쇠(접속 IP)마다 [windowMillis] 안에 [limit] 번까지만 — 인증 없는 공개 경로가 카카오 무료량을 태우지 않게
 * 지정 주정차 금지구역 계산 앞에 둔다(넘으면 버스정류장만 준다). 메모리에만, 오래된 열쇠는 저절로 지운다.
 */
class RateGate(private val limit: Int, private val windowMillis: Long, private val clock: () -> Long = System::currentTimeMillis) {
  private val hits = HashMap<String, ArrayDeque<Long>>()

  @Synchronized
  fun allow(key: String): Boolean {
    val now = clock()
    if (hits.size > MAX_KEYS) hits.entries.removeIf { (_, q) -> q.isEmpty() || now - q.last() >= windowMillis }
    val q = hits.getOrPut(key) { ArrayDeque() }
    while (q.isNotEmpty() && now - q.first() >= windowMillis) q.removeFirst()
    if (q.size >= limit) return false
    q.addLast(now)
    return true
  }

  private companion object {
    const val MAX_KEYS = 10_000
  }
}
