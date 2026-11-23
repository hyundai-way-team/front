package com.kscho.way.presentation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

@Serializable data object ChoosePassenger : NavKey

@Serializable data object SearchPlace : NavKey

@Serializable data object AdjustPin : NavKey

@Serializable data object SetDestination : NavKey

@Serializable data object ConfirmDeparture : NavKey

@Serializable data object Drive : NavKey
