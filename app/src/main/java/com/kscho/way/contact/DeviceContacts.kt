package com.kscho.way.contact

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Log
import androidx.core.content.ContextCompat
import com.kscho.way.data.dataSource.local.contact.ContactLocalDataSource
import com.kscho.way.data.dataSource.local.contact.DeviceContact
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DeviceContacts @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : ContactLocalDataSource {
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
