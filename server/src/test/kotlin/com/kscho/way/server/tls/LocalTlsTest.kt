package com.kscho.way.server.tls

import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTlsTest {
  @Test
  fun missingFiles_tellHowToMakeThem() {
    val dir = Files.createTempDirectory("way-tls").toFile()
    val error = runCatching { LocalTls.load(dir) }.exceptionOrNull()
    assertTrue(error is IllegalArgumentException && error.message!!.contains("make_local_https.sh"))
    dir.deleteRecursively()
  }
}
