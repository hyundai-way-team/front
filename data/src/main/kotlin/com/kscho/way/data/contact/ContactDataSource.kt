package com.kscho.way.data.contact

import com.kscho.way.data.remote.ContactDto

/** 운전자 연락처. 기획은 Pleos 연락처지만 공개 API 가 없어(연동 가능 여부 미정) 지금은 mock. */
interface ContactDataSource {
  suspend fun all(): List<ContactDto>
}
