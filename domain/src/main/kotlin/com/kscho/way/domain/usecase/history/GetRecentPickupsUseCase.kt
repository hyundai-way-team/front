package com.kscho.way.domain.usecase.history

import com.kscho.way.domain.model.pickup.PickupRecord
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import javax.inject.Inject

class GetRecentPickupsUseCase @Inject constructor(
  private val historyRepository: PickupHistoryRepository,
) {
  suspend operator fun invoke(limit: Int): List<PickupRecord> = historyRepository.recent(limit)
}
