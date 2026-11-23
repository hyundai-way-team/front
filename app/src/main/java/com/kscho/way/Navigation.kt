package com.kscho.way

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.pickup.PickupService
import com.kscho.way.ui.drive.DriveScreen
import com.kscho.way.ui.home.HomeScreen
import com.kscho.way.ui.plan.AdjustPinScreen
import com.kscho.way.ui.plan.ChoosePassengerScreen
import com.kscho.way.ui.plan.ConfirmDepartureScreen
import com.kscho.way.ui.plan.PickupDraft
import com.kscho.way.ui.plan.SearchPlaceScreen
import com.kscho.way.ui.plan.SetDestinationScreen

@Composable
fun MainNavigation(coordinator: PickupCoordinator, draft: PickupDraft) {
  val context = LocalContext.current
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
              startPickupService(context)
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

private fun startPickupService(context: Context) {
  PickupService.start(context)
}
