package com.kscho.way.domain.model.base

sealed interface ApiState<out T> {
  data class Success<T>(val data: T) : ApiState<T>

  data class Fail(val failState: FailState) : ApiState<Nothing>
}

data class FailState(val code: Int, val message: String)

class ApiFailureException(val failState: FailState) : Exception(failState.message)

inline fun <T, R> ApiState<T>.map(transform: (T) -> R): ApiState<R> =
  when (this) {
    is ApiState.Success -> ApiState.Success(transform(data))
    is ApiState.Fail -> this
  }

fun <T> ApiState<T>.getOrNull(): T? = (this as? ApiState.Success)?.data

fun <T> ApiState<T>.getOrThrow(): T =
  when (this) {
    is ApiState.Success -> data
    is ApiState.Fail -> throw ApiFailureException(failState)
  }
