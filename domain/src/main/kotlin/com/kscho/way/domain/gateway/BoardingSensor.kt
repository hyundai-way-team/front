package com.kscho.way.domain.gateway

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BoardingSensor {
  val available: StateFlow<Boolean?>

  val boardings: SharedFlow<Long>
}
