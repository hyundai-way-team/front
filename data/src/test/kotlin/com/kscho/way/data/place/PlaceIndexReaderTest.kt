package com.kscho.way.data.place

import com.kscho.way.domain.model.LatLng
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** build_places_index.py 가 쓰는 형식을 그대로 손으로 만들어 읽어 본다. */
class PlaceIndexReaderTest {
  /** 파이썬 Out 과 같은 LEB128 / 지그재그 쓰개. */
  private class Out {
    val bytes = ByteArrayOutputStream()

    fun u(v: Int) {
      var x = v
      while (true) {
        val b = x and 0x7F
        x = x ushr 7
        if (x != 0) bytes.write(b or 0x80) else return bytes.write(b)
      }
    }

    fun s(v: Int) = u((v shl 1) xor (v shr 31))

    fun raw(b: ByteArray) = bytes.write(b)
  }

  private fun sample(): ByteArray {
    val strings = listOf("서울 마포구", "연남동", "신촌자이엘라 버스정류장", "bus", "bus_stop", "신촌로", "서울")
    val out = Out()
    out.raw("WAYP".toByteArray())
    out.u(1)
    val baseLat = 37_550_000
    val baseLng = 126_930_000
    out.u(baseLat)
    out.u(baseLng)
    out.u(strings.size)
    for (s in strings) {
      val b = s.toByteArray(Charsets.UTF_8)
      out.u(b.size)
      out.raw(b)
    }
    // 종류 1: bus/bus_stop
    out.u(1)
    out.u(3)
    out.u(4)
    // 동네 1: 연남동(동, 윗주소 "서울 마포구")
    out.u(1)
    out.u(1)
    out.u(AreaLevel.QUARTER.ordinal)
    out.u(0)
    out.s(12_580)
    out.s(-8_041)
    // 장소 2: 정류장 둘(두 번째는 첫 것에서 남쪽으로 −300, 동네 모름, 길에서 안 보임)
    out.u(2)
    out.u(2)
    out.u(0)
    out.u(1) // 동네 0 + 1
    out.u(0)
    out.s(6_783)
    out.s(12_649)
    out.u(2)
    out.u(0)
    out.u(0)
    out.u(PlaceIndex.FLAG_NOT_STREET)
    out.s(-300)
    out.s(0)
    // 도로 1: 신촌로(primary) 점 3개 — 좌표는 바로 앞 점(앞 묶음 아님, 도로 묶음 기준에서 시작)
    out.u(1)
    out.u(5)
    out.u(RoadClass.PRIMARY.ordinal)
    out.u(3)
    out.s(6_700)
    out.s(11_000)
    out.s(0)
    out.s(1_000)
    out.s(-50)
    out.s(1_000)
    return out.bytes.toByteArray()
  }

  private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

  @Test
  fun readsGzipAndPlain() {
    for (bytes in listOf(gzip(sample()), sample())) {
      val index = PlaceIndexReader.read(ByteArrayInputStream(bytes))
      assertEquals(1, index.areaCount)
      assertEquals(PlaceIndex.Area("연남동", AreaLevel.QUARTER, "서울 마포구", LatLng(37.56258, 126.921959)), index.area(0))
      assertEquals(2, index.poiCount)
      assertEquals(PlaceIndex.Poi("신촌자이엘라 버스정류장", "bus", "bus_stop", LatLng(37.556783, 126.942649), 0, true), index.poi(0))
      assertEquals(PlaceIndex.Poi("신촌자이엘라 버스정류장", "bus", "bus_stop", LatLng(37.556483, 126.942649), -1, false), index.poi(1))
      assertEquals("서울 마포구 연남동", index.poiAddress(0))
      assertEquals(1, index.roadCount)
      assertEquals(
        PlaceIndex.Road("신촌로", RoadClass.PRIMARY, listOf(LatLng(37.5567, 126.941), LatLng(37.5567, 126.942), LatLng(37.55665, 126.943))),
        index.road(0),
      )
    }
  }

  @Test
  fun rejectsOtherFiles() {
    assertThrows(IOException::class.java) { PlaceIndexReader.read(ByteArrayInputStream("NOPE".toByteArray())) }
    val truncated = sample().copyOf(40)
    assertThrows(IOException::class.java) { PlaceIndexReader.read(ByteArrayInputStream(truncated)) }
  }
}
