package com.kscho.way.presentation.drive

internal object DriveCamera {
  const val FAR_METERS_PER_DP = 1.6f

  const val ZOOM_IN_REMAINING_METERS = 500

  const val CLOSE_METERS_PER_DP = 0.6f

  const val NEAR_METERS_PER_DP = 0.3f

  fun zoomedIn(wasZoomedIn: Boolean, remainingMeters: Int?): Boolean =
    wasZoomedIn || (remainingMeters != null && remainingMeters <= ZOOM_IN_REMAINING_METERS)

  fun metersPerDp(zoomedIn: Boolean): Float = if (zoomedIn) CLOSE_METERS_PER_DP else FAR_METERS_PER_DP
}
