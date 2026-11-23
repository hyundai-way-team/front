package com.kscho.way.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.pickup.PickupDraft
import com.kscho.way.presentation.drive.DriveScreen
import com.kscho.way.presentation.home.HomeScreen
import com.kscho.way.presentation.plan.AdjustPinScreen
import com.kscho.way.presentation.plan.ChoosePassengerScreen
import com.kscho.way.presentation.plan.ConfirmDepartureScreen
import com.kscho.way.presentation.plan.SearchPlaceScreen
import com.kscho.way.presentation.plan.SetDestinationScreen
@Composable
fun MainNavigation(coordinator: PickupCoordinator, draft: PickupDraft, onPickupStarted: () -> Unit) {
  val pickup by coordinator.state.collectAsStateWithLifecycle()
  val backStack = rememberNavBackStack(if (coordinator.state.value != null) Drive else Home)

  LaunchedEffect(pickup?.id) {
    val top = backStack.lastOrNull()
    if (pickup != null && top != Drive) backStack.replaceAll(Drive)
    if (pickup == null && top == Drive) backStack.replaceAll(Home)
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider =
      entryProvider {
        entry<Home> {
          HomeScreen(
            onCreatePickup = {
              draft.reset()
              backStack.add(ChoosePassenger)
            },
            onReuse = { record ->
              draft.reuse(record)
              backStack.add(AdjustPin)
            },
          )
        }
        entry<ChoosePassenger> { ChoosePassengerScreen(onBack = { backStack.removeLastOrNull() }, onNext = { backStack.add(SearchPlace) }) }
        entry<SearchPlace> { SearchPlaceScreen(onBack = { backStack.removeLastOrNull() }, onNext = { backStack.add(AdjustPin) }) }
        entry<AdjustPin> {
          AdjustPinScreen(
            onBack = { backStack.removeLastOrNull() },
            onSearchAgain = {
              backStack.removeLastOrNull()
              if (backStack.lastOrNull() != SearchPlace) backStack.add(SearchPlace)
            },
            onConfirmed = { backStack.add(SetDestination) },
          )
        }
        entry<SetDestination> { SetDestinationScreen(onBack = { backStack.removeLastOrNull() }, onNext = { backStack.add(ConfirmDeparture) }) }
        entry<ConfirmDeparture> {
          ConfirmDepartureScreen(
            onBack = { backStack.removeLastOrNull() },
            onStarted = {
              onPickupStarted()
              backStack.replaceAll(Drive)
            },
          )
        }
        entry<Drive> { DriveScreen(onClosed = { backStack.replaceAll(Home) }) }
      },
  )
}

private fun NavBackStack<NavKey>.replaceAll(key: NavKey) {
  if (size == 1 && first() == key) return
  add(key)
  removeAll { it != key }
}
