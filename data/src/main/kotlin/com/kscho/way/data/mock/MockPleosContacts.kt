package com.kscho.way.data.mock

import com.kscho.way.data.dataSource.local.contact.ContactLocalDataSource
import com.kscho.way.data.dataSource.local.contact.DeviceContact

class MockPleosContacts : ContactLocalDataSource {
  override suspend fun all(): List<DeviceContact> =
    listOf(
      DeviceContact("c-seoyeon", "이서연", "01000001234"),
      DeviceContact("c-minjun", "김민준", "01000005678"),
      DeviceContact("c-jiho", "박지호", "01000009012"),
      DeviceContact("c-yuna", "최유나", "01000003456"),
      DeviceContact("c-hajun", "정하준", "01000007890"),
    )
}
