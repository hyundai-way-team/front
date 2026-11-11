package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.PickupOutcome
import com.kscho.way.domain.model.PickupRecord

/** 지난 픽업 기록 규칙 — 앱에 두는 홈 '최근 픽업'. */
object PickupRecords {
  /** 이만큼만 남긴다. */
  const val MAX = 20

  /** 끝난 픽업 1건 → 기록. */
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

  /** 새 기록을 맨 앞에. 같은 사람(번호)·같은 픽업 위치(제목)는 최근 것 하나만, 최대 [max]건. */
  fun add(history: List<PickupRecord>, record: PickupRecord, max: Int = MAX): List<PickupRecord> =
    (listOf(record) + history.filterNot { it.isSamePickupAs(record) }).take(max)

  private fun PickupRecord.isSamePickupAs(other: PickupRecord): Boolean =
    passenger.phone.filter(Char::isDigit) == other.passenger.phone.filter(Char::isDigit) &&
      pickupPoint.description.title == other.pickupPoint.description.title
}
