package com.kscho.way.server.tls

import java.util.Base64
import java.util.UUID

/**
 * 아이폰용 구성 프로파일(.mobileconfig) — 로컬 https 의 CA 공개 인증서 하나를 '루트 인증서'로 담는다.
 * 사파리는 그냥 .cer 보다 이 형식을 받을 때 '프로파일 다운로드됨 → 설정에서 설치' 흐름을 확실히 띄운다.
 * 서명하지 않은 프로파일이라 설치 화면에 '서명되지 않음'으로 보인다(시연용).
 */
object RootCaProfile {
  const val CONTENT_TYPE = "application/x-apple-aspen-config"

  fun mobileConfig(caDer: ByteArray, name: String = "Way Dev CA"): String {
    // 같은 인증서면 같은 UUID — 다시 받아도 같은 프로파일로 보인다
    val profileId = UUID.nameUUIDFromBytes(caDer + "profile".toByteArray())
    val certId = UUID.nameUUIDFromBytes(caDer + "cert".toByteArray())
    val cert = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(caDer)
    return """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
      |<plist version="1.0">
      |<dict>
      |  <key>PayloadContent</key>
      |  <array>
      |    <dict>
      |      <key>PayloadCertificateFileName</key>
      |      <string>way-dev-ca.cer</string>
      |      <key>PayloadContent</key>
      |      <data>
      |$cert
      |      </data>
      |      <key>PayloadDescription</key>
      |      <string>Way 로컬 서버 https 인증서</string>
      |      <key>PayloadDisplayName</key>
      |      <string>${xml(name)}</string>
      |      <key>PayloadIdentifier</key>
      |      <string>com.kscho.way.dev.ca.$certId</string>
      |      <key>PayloadType</key>
      |      <string>com.apple.security.root</string>
      |      <key>PayloadUUID</key>
      |      <string>$certId</string>
      |      <key>PayloadVersion</key>
      |      <integer>1</integer>
      |    </dict>
      |  </array>
      |  <key>PayloadDescription</key>
      |  <string>동승자 웹에서 실제 위치 공유를 시험하려고 이 맥의 로컬 https 인증서를 신뢰합니다. 시연이 끝나면 지워 주세요.</string>
      |  <key>PayloadDisplayName</key>
      |  <string>Way 로컬 https (시연용)</string>
      |  <key>PayloadIdentifier</key>
      |  <string>com.kscho.way.dev.profile.$profileId</string>
      |  <key>PayloadRemovalDisallowed</key>
      |  <false/>
      |  <key>PayloadType</key>
      |  <string>Configuration</string>
      |  <key>PayloadUUID</key>
      |  <string>$profileId</string>
      |  <key>PayloadVersion</key>
      |  <integer>1</integer>
      |</dict>
      |</plist>
      |"""
      .trimMargin()
  }

  private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
