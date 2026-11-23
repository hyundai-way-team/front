package com.kscho.way.data.dataSource.remote

import com.kscho.way.data.response.PassengerStatusResponse
import kotlinx.coroutines.flow.Flow

interface PassengerSocketDataSource {
  fun passengerUpdates(pickupId: String): Flow<PassengerStatusResponse>
}
