package com.kscho.way.data.fake

import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupPoint
import com.kscho.way.domain.model.pickup.PickupRecord
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place
import java.time.DayOfWeek
import java.time.ZoneId

object FakeWorld {
  val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

  val SINCHON_RO_NORTH =
    listOf(
      LatLng(37.559084, 126.961543),
      LatLng(37.558578, 126.960899),
      LatLng(37.557608, 126.959703),
      LatLng(37.557370, 126.959150),
      LatLng(37.557332, 126.958818),
      LatLng(37.557417, 126.957321),
      LatLng(37.557489, 126.956211),
      LatLng(37.556851, 126.945852),
      LatLng(37.556673, 126.942827),
      LatLng(37.556664, 126.942580),
      LatLng(37.556588, 126.941534),
      LatLng(37.556528, 126.941029),
      LatLng(37.556120, 126.939812),
      LatLng(37.555818, 126.938825),
      LatLng(37.555363, 126.937462),
      LatLng(37.555316, 126.936690),
    )

  val SINCHON_RO_SOUTH =
    listOf(
      LatLng(37.555235, 126.936271),
      LatLng(37.555082, 126.937006),
      LatLng(37.555091, 126.937199),
      LatLng(37.556171, 126.940477),
      LatLng(37.556426, 126.941292),
      LatLng(37.556464, 126.941625),
      LatLng(37.556528, 126.942907),
      LatLng(37.556707, 126.945857),
      LatLng(37.556762, 126.947209),
      LatLng(37.557051, 126.951774),
      LatLng(37.557226, 126.954419),
      LatLng(37.557349, 126.956007),
      LatLng(37.557213, 126.958694),
    )

  val CAR_START = SINCHON_RO_NORTH.first()

  val VILLA_ALLEY_ENTRANCE = LatLng(37.556700, 126.942000)

  val VILLA = Geo.offset(VILLA_ALLEY_ENTRANCE, east = 0.0, north = 40.0)

  val BUS_STOP = LatLng(37.556783, 126.942649)

  data class Spot(val id: String, val name: String, val address: String, val location: LatLng)

  val places =
    listOf(
      Spot("villa", "신촌 XX 빌라", "서울 서대문구 신촌로 25길 12 (예시)", VILLA),
      Spot("bus-stop", "신촌자이엘라 버스정류장", "서울 서대문구 신촌로 (이대역 방면)", BUS_STOP),
      Spot("sinchon-stn", "신촌역 2호선", "서울 마포구 신촌로 지하 90", LatLng(37.555150, 126.936910)),
      Spot("ewha-stn", "이대역 2호선", "서울 마포구 신촌로 지하 180", LatLng(37.556775, 126.945836)),
      Spot("sinchon-rail", "신촌기차역", "서울 서대문구 신촌역로 30", LatLng(37.559781, 126.942360)),
      Spot("hyundai", "현대백화점 신촌점", "서울 서대문구 신촌로 83", LatLng(37.555970, 126.935650)),
      Spot("yonsei", "연세대학교 정문", "서울 서대문구 연세로 50", LatLng(37.560150, 126.936850)),
      Spot("ewha", "이화여자대학교 정문", "서울 서대문구 이화여대길 52", LatLng(37.559000, 126.946500)),
    )

  fun demoHistory(now: Long): List<PickupRecord> {
    val day = 24 * 60 * 60 * 1000L
    val villa = places.first { it.id == "villa" }
    val hyundai = places.first { it.id == "hyundai" }
    return listOf(
      PickupRecord(
        id = "demo-1",
        passenger = Contact("c-seoyeon", "이서연", "01000001234"),
        place = Place(villa.id, villa.name, villa.address, villa.location),
        pickupPoint = PickupPoint(VILLA_ALLEY_ENTRANCE, LocationDescription("신촌 XX 빌라 골목 입구 앞", "신촌로 · 도로 북쪽(빌라 쪽) 인도"), 40),
        boarded = true,
        pickupLocationChanges = 0,
        waitedSeconds = 38,
        endedAtMillis = now - 2 * day,
      ),
      PickupRecord(
        id = "demo-2",
        passenger = Contact("c-minjun", "김민준", "01000005678"),
        place = Place(hyundai.id, hyundai.name, hyundai.address, hyundai.location),
        pickupPoint = PickupPoint(LatLng(37.55560, 126.93580), LocationDescription("현대백화점 신촌점 정문 앞", "신촌로 · 도로 북쪽 인도"), 44),
        boarded = true,
        pickupLocationChanges = 0,
        waitedSeconds = 20,
        endedAtMillis = now - 5 * day,
      ),
    )
  }

  data class Zone(
    val id: String,
    val name: String,
    val rule: String,
    val path: List<LatLng>,
    val radiusMeters: Double,
    val days: Set<DayOfWeek>? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
  )

  private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

  val zones =
    listOf(
      Zone(
        "bus-stop",
        "버스정류장",
        "절대 주정차 금지 · 상시",
        listOf(LatLng(37.556720, 126.942400), LatLng(37.556750, 126.942900)),
        radiusMeters = 12.0,
      ),
      Zone(
        "south-curb",
        "신촌로 남쪽 길가",
        "주정차 금지 · 평일 07:00~22:00",
        SINCHON_RO_SOUTH.filter { it.longitude in 126.9370..126.9470 }.map { Geo.offset(it, east = 0.0, north = -8.0) },
        radiusMeters = 6.0,
        days = weekdays,
        startMinute = 7 * 60,
        endMinute = 22 * 60,
      ),
    )

  data class Landmark(val title: String, val location: LatLng)

  val landmarks =
    listOf(
      Landmark("신촌 XX 빌라 골목 입구 앞", VILLA_ALLEY_ENTRANCE),
      Landmark("신촌자이엘라 버스정류장 앞", BUS_STOP),
      Landmark("신촌역 앞", LatLng(37.555300, 126.937000)),
      Landmark("이대역 앞", LatLng(37.556900, 126.945900)),
    )

  fun latitudeAt(line: List<LatLng>, lng: Double): Double? {
    for ((a, b) in line.zipWithNext()) {
      val lo = minOf(a.longitude, b.longitude)
      val hi = maxOf(a.longitude, b.longitude)
      if (lng in lo..hi && hi > lo) {
        val t = (lng - a.longitude) / (b.longitude - a.longitude)
        return a.latitude + (b.latitude - a.latitude) * t
      }
    }
    return null
  }

  fun roadRoute(from: LatLng, to: LatLng): List<LatLng>? {
    val road = SINCHON_RO_NORTH
    if (Geo.distanceToPathMeters(from, road) > 80 || Geo.distanceToPathMeters(to, road) > 80) return null
    val start = road.indices.minBy { Geo.distanceMeters(road[it], from) }
    val end = (0 until road.size - 1).minBy { Geo.distanceToPathMeters(to, listOf(road[it], road[it + 1])) }
    if (end < start) return null
    return listOf(from) + road.subList(start + 1, end + 1) + to
  }
}
