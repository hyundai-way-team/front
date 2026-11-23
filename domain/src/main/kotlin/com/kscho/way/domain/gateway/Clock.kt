package com.kscho.way.domain.gateway

fun interface Clock {
  fun now(): Long
}
