package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.pickup.PickupOutcome
import com.kscho.way.domain.model.pickup.PickupRecord
object PickupRecords {
  const val MAX = 20

  fun of(state: PickupState, outcome: PickupOutcome, endedAtMillis: Long): PickupRecord =
    PickupRecord(
      id = state.id,
      passenger = state.plan.passenger,
      place = state.plan.place,
      pickupPoint = state.plan.pickupPoint,
      boarded = outcome.boarded,
      pickupLocationChanges = outcome.pickupLocationChanges,
      waitedSeconds = outcome.waitedSeconds,
      endedAtMillis = endedAtMillis,
    )

  fun add(history: List<PickupRecord>, record: PickupRecord, max: Int = MAX): List<PickupRecord> =
    (listOf(record) + history.filterNot { it.isSamePickupAs(record) }).take(max)

  private fun PickupRecord.isSamePickupAs(other: PickupRecord): Boolean =
    passenger.phone.filter(Char::isDigit) == other.passenger.phone.filter(Char::isDigit) &&
      pickupPoint.description.title == other.pickupPoint.description.title
}
