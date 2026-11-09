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
import com.kscho.way.pickup.PickupService
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.drive.DriveScreen
import com.kscho.way.ui.home.HomeScreen
import com.kscho.way.ui.plan.AdjustPinScreen
import com.kscho.way.ui.plan.ChoosePassengerScreen
import com.kscho.way.ui.plan.ConfirmDepartureScreen
import com.kscho.way.ui.plan.SearchPlaceScreen

@Composable
fun MainNavigation() {
  val c = appContainer()
  val context = LocalContext.current
  val pickup by c.coordinator.state.collectAsStateWithLifecycle()
  val backStack = rememberNavBackStack(if (c.coordinator.state.value != null) Drive else Home)

  // 픽업이 밖에서 시작·종료돼도(디버그 신호·Gleo) 화면을 맞춘다
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
              c.draft.reset()
              backStack.add(ChoosePassenger)
            },
            onReuse = { record ->
              c.draft.reuse(record)
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
              // 최근 픽업에서 바로 왔으면 장소 검색 화면이 뒤에 없다
              backStack.removeLastOrNull()
              if (backStack.lastOrNull() != SearchPlace) backStack.add(SearchPlace)
            },
            onConfirmed = { backStack.add(ConfirmDeparture) },
          )
        }
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
  add(key)
  removeAll { it != key }
}

/** 출발 직후(앱이 앞에 있을 때) 픽업 서비스를 띄운다 — 내비가 앞으로 나와도 Way 가 살아 있게. */
private fun startPickupService(context: Context) {
  PickupService.start(context)
}
