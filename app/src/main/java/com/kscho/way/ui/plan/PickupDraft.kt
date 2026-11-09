package com.kscho.way.ui.plan

import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.PickupRecord
import com.kscho.way.domain.model.Place
import com.kscho.way.domain.usecase.PickupPointCheck
import kotlinx.coroutines.flow.MutableStateFlow

/** 출발 전 화면들(D-02 동승자 → D-03 장소 → D-04 핀 → D-05 확인)이 채우는 계획. 출발하면 비운다. */
class PickupDraft {
  val passenger = MutableStateFlow<Contact?>(null)
  val place = MutableStateFlow<Place?>(null)
  /** 핀을 처음 놓을 곳(검색한 장소 또는 지난 픽업 위치). */
  val pinStart = MutableStateFlow<LatLng?>(null)
  /** 확정한 핀 확인 결과. */
  val check = MutableStateFlow<PickupPointCheck?>(null)

  fun reset() {
    passenger.value = null
    place.value = null
    pinStart.value = null
    check.value = null
  }

  /** 같은 사람을 같은 곳에서 다시 태울 때(기획 12 · 홈 '최근 픽업'). */
  fun reuse(record: PickupRecord) {
    passenger.value = record.passenger
    place.value = record.place
    pinStart.value = record.pickupPoint.location
    check.value = null
  }
}
