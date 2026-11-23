package com.kscho.way.domain.gateway

import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.message.PassengerText
interface PassengerMessenger {
  suspend fun send(to: Contact, text: PassengerText)
}
