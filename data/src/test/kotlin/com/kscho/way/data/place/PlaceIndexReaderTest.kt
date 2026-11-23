package com.kscho.way.data.place

import com.kscho.way.domain.model.geo.LatLng
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaceIndexReaderTest {
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
    out.u(1)
    out.u(3)
    out.u(4)
    out.u(1)
    out.u(1)
    out.u(AreaLevel.QUARTER.ordinal)
    out.u(0)
    out.s(12_580)
    out.s(-8_041)
    out.u(2)
    out.u(2)
    out.u(0)
    out.u(1)
    out.u(0)
    out.s(6_783)
    out.s(12_649)
    out.u(2)
    out.u(0)
    out.u(0)
    out.u(PlaceIndex.FLAG_NOT_STREET)
    out.s(-300)
    out.s(0)
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
