package com.kscho.way.data.dataSource.base

import com.kscho.way.domain.model.base.ApiState
import com.kscho.way.domain.model.base.FailState
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

const val NETWORK_ERROR_CODE = -1
const val RESPONSE_MAPPING_ERROR_CODE = -2
const val NETWORK_ERROR_MESSAGE = "네트워크 연결을 확인하고 다시 시도해 주세요."
const val RESPONSE_MAPPING_ERROR_MESSAGE = "서버 응답을 읽지 못했어요."

suspend inline fun <T> apiCall(crossinline call: suspend () -> T): ApiState<T> =
  try {
    ApiState.Success(call())
  } catch (e: CancellationException) {
    throw e
  } catch (e: HttpException) {
    ApiState.Fail(FailState(e.code(), e.errorMessage()))
  } catch (e: SerializationException) {
    ApiState.Fail(FailState(RESPONSE_MAPPING_ERROR_CODE, RESPONSE_MAPPING_ERROR_MESSAGE))
  } catch (e: IOException) {
    ApiState.Fail(FailState(NETWORK_ERROR_CODE, NETWORK_ERROR_MESSAGE))
  }

fun HttpException.errorMessage(): String =
  runCatching { response()?.errorBody()?.string() }.getOrNull()?.takeIf { it.isNotBlank() } ?: message()
