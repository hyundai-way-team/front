package com.kscho.way.data.repository.history

import com.kscho.way.data.dataSource.local.history.PickupHistoryFileDataSource
import com.kscho.way.data.fake.FakeWorld
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.pickup.PickupRecord
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupHistoryRepositoryImplTest {
  private val dir: File = Files.createTempDirectory("way-history").toFile()
  private val file = File(dir, "pickup_history.json")
  private val now = 1_800_000_000_000L

  @After fun cleanUp() = dir.deleteRecursively().let {}

  private fun demo() = FakeWorld.demoHistory(now)

  @Test
  fun firstRun_showsSeed_thenSavedRecordsSurviveRestart() = runTest {
    val first = PickupHistoryRepositoryImpl(PickupHistoryFileDataSource(file, seed = ::demo))
    assertEquals(listOf("demo-1", "demo-2"), first.recent(5).map(PickupRecord::id))

    val again = demo().first().copy(id = "pk_new", endedAtMillis = now)
    first.save(again)

    val restarted = PickupHistoryRepositoryImpl(PickupHistoryFileDataSource(file, seed = { error("파일이 있으면 seed 를 쓰지 않는다") }))
    val recent = restarted.recent(5)
    assertEquals(listOf("pk_new", "demo-2"), recent.map(PickupRecord::id))
    assertEquals(again, recent.first())
  }

  @Test
  fun brokenFile_startsEmpty() = runTest {
    file.writeText("{ 깨진 파일")
    val history = PickupHistoryRepositoryImpl(PickupHistoryFileDataSource(file, seed = ::demo))
    assertTrue(history.recent(5).isEmpty())

    val record = demo().last().copy(passenger = Contact("c-jiho", "박지호", "01000009012"))
    history.save(record)
    assertEquals(listOf(record), history.recent(5))
  }
}
