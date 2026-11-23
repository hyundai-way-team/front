package com.kscho.way.data.repository.history

import com.kscho.way.data.dataSource.local.history.PickupHistoryLocalDataSource
import com.kscho.way.domain.model.pickup.PickupRecord
import com.kscho.way.domain.pickup.PickupRecords
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PickupHistoryRepositoryImpl @Inject constructor(
  private val pickupHistoryLocalDataSource: PickupHistoryLocalDataSource,
) : PickupHistoryRepository {
  private val lock = Mutex()

  override suspend fun recent(limit: Int): List<PickupRecord> = lock.withLock { pickupHistoryLocalDataSource.load().take(limit) }

  override suspend fun save(record: PickupRecord) {
    lock.withLock { pickupHistoryLocalDataSource.save(PickupRecords.add(pickupHistoryLocalDataSource.load(), record)) }
  }
}
