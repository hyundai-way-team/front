package com.kscho.way.server.tls

import java.io.File
import java.security.KeyStore

/**
 * 시연용 로컬 https — tools/dev/make_local_https.sh 가 만든 폴더(WAY_TLS_DIR, 기본 ~/.way-dev-tls)를 읽는다.
 * 휴대폰 사파리는 https 에서만 위치 공유를 허락해서, 같은 네트워크의 아이폰으로 실제 위치 공유를 시험할 때 쓴다.
 *
 * keystore.p12(서버 인증서·키) · keystore.pass(그 비밀번호) · ca.cer(아이폰에 신뢰 등록할 CA 공개 인증서)
 */
class LocalTls(val keyStore: KeyStore, val password: CharArray, val caCert: File) {
  companion object {
    /** make_local_https.sh 가 키에 붙이는 이름 */
    const val KEY_ALIAS = "way"

    fun load(dir: File): LocalTls {
      val p12 = File(dir, "keystore.p12")
      val pass = File(dir, "keystore.pass")
      val ca = File(dir, "ca.cer")
      require(p12.isFile && pass.isFile && ca.isFile) { "$dir 에 인증서가 없어요 — tools/dev/make_local_https.sh 를 먼저 돌려 주세요" }
      val password = pass.readText().trim().toCharArray()
      val keyStore = KeyStore.getInstance("PKCS12").apply { p12.inputStream().use { load(it, password) } }
      require(keyStore.containsAlias(KEY_ALIAS)) { "$p12 에 '$KEY_ALIAS' 키가 없어요" }
      return LocalTls(keyStore, password, ca)
    }
  }
}
