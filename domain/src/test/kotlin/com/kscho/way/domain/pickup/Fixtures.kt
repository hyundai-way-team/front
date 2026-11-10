package com.kscho.way.domain.pickup

import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationDescription
import com.kscho.way.domain.model.MyVehicle
import com.kscho.way.domain.model.PickupPlan
import com.kscho.way.domain.model.PickupPoint
import com.kscho.way.domain.model.RoutePreview
import com.kscho.way.domain.model.StopRestriction

object Fixtures {
  val seoyeon = Contact("c1", "이서연", "01000001234")
  val pickupLocation = LatLng(37.55545, 126.93900)

  fun plan(durationSeconds: Int = 12 * 60) =
    PickupPlan(
      passenger = seoyeon,
      place = null,
      pickupPoint = PickupPoint(pickupLocation, LocationDescription("신촌 XX 빌라 골목 입구 앞", "신촌로 · 도로 북쪽 인도"), 40),
      restriction = StopRestriction.NotFound,
      route = RoutePreview(distanceMeters = 4_000, durationSeconds = durationSeconds),
      vehicle = MyVehicle("흰색 아반떼", "12가 3456", "김도윤"),
    )

  fun state(startedAt: Long = 0L, durationSeconds: Int = 12 * 60) =
    PickupState(
      id = "p1",
      shareUrl = "https://way.mock/p/1",
      plan = plan(durationSeconds),
      startedAtMillis = startedAt,
      arrivalAtMillis = startedAt + durationSeconds * 1000L,
    )
}
