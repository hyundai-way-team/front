package com.kscho.way.gleo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object GleoActions {
  const val GET_PICKUP_STATUS = "GET_PICKUP_STATUS"
  const val CALL_PASSENGER = "CALL_PASSENGER"
  const val COMPLETE_BOARDING = "COMPLETE_BOARDING"

  const val EXTRA_ACTION_PARAM = "ACTION_PARAM"

  const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
  const val EXTRA_PACKAGE_NAME = "packageName"
  const val EXTRA_RESPONSE = "response"

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
        data?.let { rows ->
          put("data", JsonArray(rows.map { row -> JsonObject(row.mapValues { JsonPrimitive(it.value) }) }).toString())
        }
      }
      .toString()
}
