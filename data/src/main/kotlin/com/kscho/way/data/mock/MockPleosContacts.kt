package com.kscho.way.data.mock

import com.kscho.way.data.contact.ContactDataSource
import com.kscho.way.data.remote.ContactDto

/** 시연용 연락처(번호는 가짜). 이서연 = MVP 페르소나 동승자. */
class MockPleosContacts : ContactDataSource {
  override suspend fun all(): List<ContactDto> =
    listOf(
      ContactDto("c-seoyeon", "이서연", "01000001234"),
      ContactDto("c-minjun", "김민준", "01000005678"),
      ContactDto("c-jiho", "박지호", "01000009012"),
      ContactDto("c-yuna", "최유나", "01000003456"),
      ContactDto("c-hajun", "정하준", "01000007890"),
    )
}
