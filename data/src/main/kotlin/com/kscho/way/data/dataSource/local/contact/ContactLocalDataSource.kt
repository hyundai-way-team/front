package com.kscho.way.data.dataSource.local.contact

import com.kscho.way.data.dataSource.local.contact.DeviceContact

interface ContactLocalDataSource {
  suspend fun all(): List<DeviceContact>
}
