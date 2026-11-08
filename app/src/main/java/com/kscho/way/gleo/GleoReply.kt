package com.kscho.way.gleo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Gleo Interface 상수.
 * 문서: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts
 */
object GleoActions {
  // assets/schema.json 의 function name 과 같아야 한다 (Gleo 가 이 이름을 Intent action 으로 보낸다)
  const val GET_PICKUP_STATUS = "GET_PICKUP_STATUS"
  const val CALL_PASSENGER = "CALL_PASSENGER"
  const val COMPLETE_BOARDING = "COMPLETE_BOARDING"

  /** Gleo 가 넣어 주는 파라미터 JSON. Way 의 세 기능은 파라미터가 없다. */
  const val EXTRA_ACTION_PARAM = "ACTION_PARAM"

  /** 앱 → Gleo 응답 브로드캐스트. */
  const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
  const val EXTRA_PACKAGE_NAME = "packageName"
  const val EXTRA_RESPONSE = "response"

  /**
   * 응답을 받는 Gleo AI 앱. 응답에 동승자 이름·ETA 가 들어가므로 다른 앱이 엿듣지 못하게
   * 브로드캐스트를 이 패키지로만 보낸다. (Pleos Connect Emulator 2.1.0 에서 확인한 패키지명)
   */
  const val GLEO_PACKAGE = "ai.umos.ambientai"
}

enum class GleoStatus(val wire: String) {
  SUCCESS("success"),
  ASKING("asking"),
  FAIL("fail"),
}

enum class GleoMode(val wire: String) {
  VOICE("voice"),
  BEEP("beep"),
  SILENT("silent"),
}

/**
 * Gleo Callback Schema 한 건. message 는 영어 문장(이름·장소 같은 한국어 고유명은 섞여도 됨) —
 * Gleo 가 이걸 바탕으로 운전자에게 들려줄 답을 만든다.
 */
data class GleoReply(
  val functionName: String,
  val status: GleoStatus,
  val message: String,
  val mode: GleoMode = GleoMode.VOICE,
  val data: List<Map<String, String>>? = null,
) {
  fun toJson(): String =
    buildJsonObject {
        put("functionName", functionName)
        put("status", status.wire)
        put("message", message)
        put("mode", mode.wire)
        // data 는 'JSON 객체 배열'을 문자열로 담는다 (Callback Schema 규칙)
        data?.let { rows ->
          put("data", JsonArray(rows.map { row -> JsonObject(row.mapValues { JsonPrimitive(it.value) }) }).toString())
        }
      }
      .toString()
}
