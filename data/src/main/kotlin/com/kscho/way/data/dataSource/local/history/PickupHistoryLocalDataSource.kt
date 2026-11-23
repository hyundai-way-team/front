package com.kscho.way.data.dataSource.local.history

import com.kscho.way.domain.model.pickup.PickupRecord

interface PickupHistoryLocalDataSource {
  suspend fun load(): List<PickupRecord>

  suspend fun save(records: List<PickupRecord>)
}
