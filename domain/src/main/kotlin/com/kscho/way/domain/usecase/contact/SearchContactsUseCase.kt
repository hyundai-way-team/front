package com.kscho.way.domain.usecase.contact

import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.repository.contact.ContactRepository
import javax.inject.Inject

class SearchContactsUseCase @Inject constructor(
  private val contactRepository: ContactRepository,
) {
  suspend operator fun invoke(query: String): List<Contact> = contactRepository.search(query)
}
