package com.kscho.way.data.history

import com.kscho.way.data.mock.MockWorld
import com.kscho.way.domain.model.Contact
import com.kscho.way.domain.model.PickupRecord
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 홈 '최근 픽업'은 앱 파일에 둔다 — 서버에 묻지 않는다. */
class FilePickupHistoryTest {
  private val dir: File = Files.createTempDirectory("way-history").toFile()
  private val file = File(dir, "pickup_history.json")
  private val now = 1_800_000_000_000L

  @After fun cleanUp() = dir.deleteRecursively().let {}

  private fun demo() = MockWorld.demoHistory(now)

  @Test
  fun firstRun_showsSeed_thenSavedRecordsSurviveRestart() = runTest {
    val first = FilePickupHistory(file, seed = ::demo)
    assertEquals(listOf("demo-1", "demo-2"), first.recent(5).map(PickupRecord::id))

    val again = demo().first().copy(id = "pk_new", endedAtMillis = now)
    first.save(again)

    // 앱을 다시 켜도(새 인스턴스) 남아 있고, 같은 사람·같은 위치는 최근 것 하나만
    val restarted = FilePickupHistory(file, seed = { error("파일이 있으면 seed 를 쓰지 않는다") })
    val recent = restarted.recent(5)
    assertEquals(listOf("pk_new", "demo-2"), recent.map(PickupRecord::id))
    assertEquals(again, recent.first()) // 연락처 id·장소·핀 설명까지 그대로
  }

  @Test
  fun brokenFile_startsEmpty() = runTest {
    file.writeText("{ 깨진 파일")
    val history = FilePickupHistory(file, seed = ::demo)
    assertTrue(history.recent(5).isEmpty())

    val record = demo().last().copy(passenger = Contact("c-jiho", "박지호", "01000009012"))
    history.save(record)
    assertEquals(listOf(record), history.recent(5))
  }
}
