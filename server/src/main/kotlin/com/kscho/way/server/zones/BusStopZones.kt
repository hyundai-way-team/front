package com.kscho.way.server.zones

import com.kscho.way.data.remote.LatLngDto
import com.kscho.way.data.remote.ZoneDto
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.cos
import kotlin.math.floor

/**
 * 정차 금지 구간 — 실제 버스정류장 위치(국토교통부_전국 버스정류장 위치정보, 공공데이터포털 15067528) 둘레 10m.
 * 도로교통법 제32조 제4호: 버스정류장 표지판 등으로부터 10m 이내는 시간과 상관없이 정차·주차 금지.
 * 데이터는 tools/data/build_bus_stops.py 가 수도권만 추려 resources/data/bus_stops_capital.csv.gz 로 둔다.
 *
 * 핀 둘레를 빨리 찾으려고 위경도 0.01° 칸(약 1km)으로 나눠 둔다.
 */
class BusStopZones(private val stops: List<Stop>) {
  data class Stop(val id: String, val name: String, val lat: Double, val lng: Double)

  private val grid: Map<Long, List<Stop>> = stops.groupBy { key(cell(it.lat), cell(it.lng)) }

  val size: Int
    get() = stops.size

  /** [radiusMeters] 안의 정류장마다 10m 정차 금지 구간 하나. */
  fun near(lat: Double, lng: Double, radiusMeters: Int): List<ZoneDto> {
    val center = LatLng(lat, lng)
    val dLat = radiusMeters / 111_320.0
    val dLng = radiusMeters / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.1))
    val out = ArrayList<ZoneDto>()
    for (y in cell(lat - dLat)..cell(lat + dLat)) {
      for (x in cell(lng - dLng)..cell(lng + dLng)) {
        for (s in grid[key(y, x)].orEmpty()) {
          if (Geo.distanceMeters(center, LatLng(s.lat, s.lng)) <= radiusMeters) out += s.zone()
        }
      }
    }
    return out
  }

  private fun Stop.zone() =
    ZoneDto(
      id = "bus-$id",
      name = "$name 버스정류장",
      rule = "버스정류장 10m 이내 · 상시 정차·주차 금지",
      path = listOf(LatLngDto(lat, lng)),
      radiusMeters = RADIUS_METERS,
    )

  companion object {
    const val RADIUS_METERS = 10.0
    private const val CELL = 0.01
    private const val RESOURCE = "/data/bus_stops_capital.csv.gz"

    private fun cell(v: Double) = floor(v / CELL).toInt()

    private fun key(y: Int, x: Int) = (y.toLong() shl 32) or (x.toLong() and 0xffffffffL)

    /** 열: id,name,lat,lng (첫 줄은 머리글). 모양이 틀린 줄은 건너뛴다. */
    fun parse(input: InputStream): BusStopZones =
      BusStopZones(
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
          lines
            .drop(1)
            .mapNotNull { line ->
              val p = splitCsv(line)
              if (p.size < 4) return@mapNotNull null
              val lat = p[2].toDoubleOrNull() ?: return@mapNotNull null
              val lng = p[3].toDoubleOrNull() ?: return@mapNotNull null
              Stop(p[0], p[1], lat, lng)
            }
            .toList()
        }
      )

    /** jar 에 든 수도권 정류장. */
    fun bundled(): BusStopZones {
      val stream = BusStopZones::class.java.getResourceAsStream(RESOURCE) ?: error("정류장 데이터가 없어요: $RESOURCE")
      return GZIPInputStream(stream).use { parse(it) }
    }

    /** 따옴표로 감싼 칸(이름에 쉼표)까지 읽는 짧은 CSV 나누기. */
    private fun splitCsv(line: String): List<String> {
      val out = ArrayList<String>(4)
      val cur = StringBuilder()
      var quoted = false
      var i = 0
      while (i < line.length) {
        val ch = line[i]
        when {
          ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
            cur.append('"')
            i++
          }
          ch == '"' -> quoted = !quoted
          ch == ',' && !quoted -> {
            out += cur.toString()
            cur.setLength(0)
          }
          else -> cur.append(ch)
        }
        i++
      }
      out += cur.toString()
      return out
    }
  }
}
