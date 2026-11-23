package com.kscho.way.data.dataSource.local.contact

import kotlinx.serialization.Serializable

@Serializable data class DeviceContact(val id: String, val name: String, val phone: String)
