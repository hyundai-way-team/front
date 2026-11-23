package com.kscho.way.domain.repository.history

import com.kscho.way.domain.model.pickup.PickupRecord
interface PickupHistoryRepository {
  suspend fun recent(limit: Int): List<PickupRecord>

  suspend fun save(record: PickupRecord)
}
