package com.kscho.way.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kscho.way.presentation.drive.DriveRoute
import com.kscho.way.presentation.home.HomeRoute
import com.kscho.way.presentation.plan.AdjustPinRoute
import com.kscho.way.presentation.plan.ChoosePassengerRoute
import com.kscho.way.presentation.plan.ConfirmDepartureRoute
import com.kscho.way.presentation.plan.SearchPlaceRoute
import com.kscho.way.presentation.plan.SetDestinationRoute
@Composable
fun MainNavigation(onPickupStarted: () -> Unit, viewModel: MainViewModel = hiltViewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val backStack = rememberNavBackStack(if (state.activePickupId != null) Drive else Home)

  LaunchedEffect(state.activePickupId) {
    val top = backStack.lastOrNull()
    if (state.activePickupId != null && top != Drive) backStack.replaceAll(Drive)
    if (state.activePickupId == null && top == Drive) backStack.replaceAll(Home)
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider =
      entryProvider {
        entry<Home> {
          HomeRoute(
            onNavigateToChoosePassenger = { backStack.add(ChoosePassenger) },
            onNavigateToAdjustPin = { backStack.add(AdjustPin) },
          )
        }
        entry<ChoosePassenger> {
          ChoosePassengerRoute(onBack = { backStack.removeLastOrNull() }, onNavigateToSearchPlace = { backStack.add(SearchPlace) })
        }
        entry<SearchPlace> {
          SearchPlaceRoute(onBack = { backStack.removeLastOrNull() }, onNavigateToAdjustPin = { backStack.add(AdjustPin) })
        }
        entry<AdjustPin> {
          AdjustPinRoute(
            onBack = { backStack.removeLastOrNull() },
            onSearchAgain = {
              backStack.removeLastOrNull()
              if (backStack.lastOrNull() != SearchPlace) backStack.add(SearchPlace)
            },
            onNavigateToSetDestination = { backStack.add(SetDestination) },
          )
        }
        entry<SetDestination> {
          SetDestinationRoute(onBack = { backStack.removeLastOrNull() }, onNavigateToConfirmDeparture = { backStack.add(ConfirmDeparture) })
        }
        entry<ConfirmDeparture> {
          ConfirmDepartureRoute(
            onBack = { backStack.removeLastOrNull() },
            onPickupStarted = {
              onPickupStarted()
              backStack.replaceAll(Drive)
            },
          )
        }
        entry<Drive> { DriveRoute(onClosed = { backStack.replaceAll(Home) }) }
      },
  )
}

private fun NavBackStack<NavKey>.replaceAll(key: NavKey) {
  if (size == 1 && first() == key) return
  add(key)
  removeAll { it != key }
}
