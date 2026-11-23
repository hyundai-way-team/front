package com.kscho.way.domain.repository.contact

import com.kscho.way.domain.model.contact.Contact
interface ContactRepository {
  suspend fun search(query: String): List<Contact>
}
