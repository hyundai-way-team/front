package com.kscho.way.data.repository.contact

import com.kscho.way.data.dataSource.local.contact.ContactLocalDataSource
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.repository.contact.ContactRepository
import com.kscho.way.domain.repository.history.PickupHistoryRepository
import javax.inject.Inject

class ContactRepositoryImpl @Inject constructor(
  private val contactLocalDataSource: ContactLocalDataSource,
  private val pickupHistoryRepository: PickupHistoryRepository,
) : ContactRepository {
  override suspend fun search(query: String): List<Contact> {
    val device = contactLocalDataSource.all().map { Contact(it.id, it.name, it.phone) }
    val byDigits = device.associateBy { it.phone.digits() }
    val recent = runCatching { pickupHistoryRepository.recent(RECENT_PASSENGERS).map { it.passenger } }.getOrDefault(emptyList())
    val firsts = recent.map { byDigits[it.phone.digits()] ?: it }
    val all = (firsts + device).distinctBy { it.phone.digits() }
    val q = query.trim()
    if (q.isEmpty()) return all
    val digits = q.digits()
    return all.filter { c -> c.name.contains(q) || (digits.isNotEmpty() && c.phone.digits().contains(digits)) }
  }

  private fun String.digits(): String = filter(Char::isDigit)

  private companion object {
    const val RECENT_PASSENGERS = 20
  }
}
