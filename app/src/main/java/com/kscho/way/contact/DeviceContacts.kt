package com.kscho.way.contact

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Log
import androidx.core.content.ContextCompat
import com.kscho.way.data.dataSource.local.contact.ContactLocalDataSource
import com.kscho.way.data.dataSource.local.contact.DeviceContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 운전자 연락처 = 기기 연락처(ContactsContract).
 * Pleos 차량은 블루투스로 연결된 휴대폰 연락처를 기기 연락처로 받아 둔다(전화 앱이 쓰는 것과 같은 곳).
 * 즐겨찾기 먼저, 그다음 이름순. 권한(READ_CONTACTS)이 없으면 빈 목록 — 화면에서 권한을 받는다.
 */
class DeviceContacts(private val context: Context) : ContactLocalDataSource {
  val granted: Boolean
    get() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

  override suspend fun all(): List<DeviceContact> =
    withContext(Dispatchers.IO) {
      if (!granted) return@withContext emptyList()
      try {
        val out = LinkedHashMap<String, DeviceContact>()
        context.contentResolver
          .query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.STARRED),
            null,
            null,
            "${Phone.STARRED} DESC, ${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
          )
          ?.use { c ->
            val id = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val name = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
            val number = c.getColumnIndexOrThrow(Phone.NUMBER)
            while (c.moveToNext()) {
              val phone = c.getString(number)?.takeIf { it.any(Char::isDigit) } ?: continue
              val digits = phone.filter(Char::isDigit)
              // 한 사람이 번호를 여러 개 가지면 줄마다 따로 보이되, 같은 번호는 한 번만
              if (digits in out) continue
              out[digits] = DeviceContact("c${c.getLong(id)}-$digits", c.getString(name) ?: phone, phone)
            }
          }
        out.values.toList()
      } catch (e: SecurityException) {
        Log.w("WayContacts", "연락처 권한 없음", e)
        emptyList()
      }
    }
}
