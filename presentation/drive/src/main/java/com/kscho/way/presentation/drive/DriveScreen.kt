package com.kscho.way.presentation.drive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupPlan
import com.kscho.way.domain.model.pickup.PickupPoint
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.restriction.StopRestriction
import com.kscho.way.domain.model.route.RoutePreview
import com.kscho.way.domain.model.vehicle.MyVehicle
import com.kscho.way.domain.pickup.PickupPhase
import com.kscho.way.domain.pickup.PickupPolicy
import com.kscho.way.domain.pickup.PickupState
import com.kscho.way.presentation.component.base.CollectUiEvents
import com.kscho.way.presentation.component.common.ActionButton
import com.kscho.way.presentation.component.common.ActionKind
import com.kscho.way.presentation.component.intent.PassengerCall
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayTheme
import com.kscho.way.presentation.drive.R
import com.kscho.way.presentation.map.MapLine
import com.kscho.way.presentation.map.MapPin
import com.kscho.way.presentation.map.PinKind
import com.kscho.way.presentation.map.WayMap
import kotlinx.coroutines.delay

@Composable
fun DriveRoute(onClosed: () -> Unit, viewModel: DriveViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      now = System.currentTimeMillis()
      delay(CLOCK_TICK_MILLIS)
    }
  }
  CollectUiEvents(viewModel.uiEvent) { event ->
    when (event) {
      DriveEvent.Closed -> onClosed()
    }
  }
  DriveScreen(
    state = state,
    now = now,
    onConfirmBoarded = viewModel::confirmBoarded,
    onFinish = viewModel::finish,
    onConnectNext = viewModel::connectNext,
    onCancelNext = viewModel::cancelNext,
    onCallPassenger = { phone -> runCatching { context.startActivity(PassengerCall.dialIntent(phone)) } },
  )
}

@Composable
fun DriveScreen(
  state: DriveUiState,
  now: Long,
  onConfirmBoarded: () -> Unit,
  onFinish: () -> Unit,
  onConnectNext: () -> Unit,
  onCancelNext: () -> Unit,
  onCallPassenger: (String) -> Unit,
) {
  val s = state.pickup ?: return
  val zones = state.zones
  val doorAvailable = state.doorAvailable
  val connecting = s.connectRemainingMillis(now) != null

  BackHandler {
    when {
      connecting -> onCancelNext()
      s.isBoarded -> onFinish()
    }
  }

  val pickup = s.plan.pickupPoint.location
  val name = s.plan.passenger.callName
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().then(if (connecting) Modifier.blur(2.dp) else Modifier)) {
      val driving = s.phase == PickupPhase.DRIVING
      val car = s.carLocation
      val covered = STATUS_CARD_EDGE + STATUS_CARD_WIDTH
      val remaining = s.remainingMeters ?: s.plan.route.distanceMeters
      val zoomLatch = remember { booleanArrayOf(false) }
      zoomLatch[0] = DriveCamera.zoomedIn(zoomLatch[0], if (driving) remaining else null)
      val zoomedIn = zoomLatch[0]
      WayMap(
        center = if (driving && car != null) car else pickup,
        metersPerDp = if (driving) DriveCamera.metersPerDp(zoomedIn) else DriveCamera.NEAR_METERS_PER_DP,
        modifier = Modifier.fillMaxSize(),
        cameraPadding = if (driving) PaddingValues(start = covered) else PaddingValues(),
        interactive = true,
        resumeFollowAfterMillis = RESUME_FOLLOW_MILLIS,
        zones = if (s.isBoarded) emptyList() else zones,
        lines =
          buildList {
            if (driving && car != null) add(MapLine(remainingPath(s), WayColors.Blue, 7f))
            val passenger = s.passenger.location
            val walk = s.passenger.walk
            if (!s.isBoarded && walk != null) add(MapLine(walk.path, WayColors.Passenger, 5f))
            else if (!s.isBoarded && passenger != null) add(MapLine(listOf(passenger, pickup), WayColors.Passenger, 3f, dashed = true))
          },
        pins =
          buildList {
            if (s.isBoarded) {
              add(MapPin(pickup, PinKind.CAR, "현재 위치"))
              return@buildList
            }
            add(MapPin(pickup, PinKind.PICKUP, "픽업 위치"))
            s.passenger.location?.let { add(MapPin(it, PinKind.PASSENGER, "${name}님")) }
            car?.let { add(MapPin(if (driving) it else pickup, PinKind.CAR)) }
          },
      )

      Column(Modifier.align(Alignment.TopStart).padding(STATUS_CARD_EDGE).width(STATUS_CARD_WIDTH), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (s.phase) {
          PickupPhase.DRIVING -> DrivingCard(s)
          PickupPhase.WAITING -> {
            StoppedCard(s)
            PassengerRouteCard(s)
          }
          PickupPhase.BOARDED -> BoardedCard(s)
        }
      }

      MapAnnotation(
        if (driving) "지도 · 네비 경로 (목적지 = 픽업 위치)" else "지도 · 픽업 위치 주변 확대",
        Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 32.dp),
      )
      Column(Modifier.align(Alignment.TopEnd).padding(top = 86.dp, end = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (s.phase) {
          PickupPhase.DRIVING -> s.nextTurn?.let { TurnCard(it) }
          PickupPhase.WAITING -> {
            val left = s.waitRemainingMillis(now) ?: 0L
            val urgent = left <= PickupPolicy.CALL_OFFER_REMAINING_SECONDS * 1000L
            WaitTimerCard(left, urgent)
            if (urgent) {
              ActionButton(
                "전화하기",
                { onCallPassenger(s.plan.passenger.phone) },
                Modifier.width(260.dp),
                kind = ActionKind.DANGER,
                icon = R.drawable.ic_phone,
              )
            }
          }
          PickupPhase.BOARDED -> BoardedBadge(s)
        }
      }

      if (!connecting) VoiceBanner(s, now, Modifier.align(Alignment.BottomStart).padding(32.dp).width(720.dp))

      if (s.isBoarded) {
        if (!connecting) ActionButton("탑승 완료", onFinish, Modifier.align(Alignment.BottomEnd).padding(32.dp).width(180.dp))
      } else if (doorAvailable == false) {
        Column(Modifier.align(Alignment.BottomEnd).padding(32.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          MapAnnotation("도어 정보를 쓸 수 없어 손으로 알려 주세요")
          ActionButton("탑승 완료", onConfirmBoarded, Modifier.width(180.dp))
        }
      }
    }

    if (connecting) {
      Box(Modifier.fillMaxSize().background(WayColors.Dim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {})
      NextStepDialog(s, onCancel = onCancelNext, onConnect = onConnectNext, Modifier.align(Alignment.Center))
      VoiceBanner(s, now, Modifier.align(Alignment.BottomCenter).padding(32.dp).fillMaxWidth())
    }
  }
}

private fun remainingPath(s: PickupState): List<LatLng> {
  val car = s.carLocation ?: return emptyList()
  val path = s.plan.route.path
  if (path.size < 2) return listOf(car, s.plan.pickupPoint.location)
  val segment = (0 until path.size - 1).minBy { Geo.distanceToPathMeters(car, listOf(path[it], path[it + 1])) }
  return listOf(car) + path.drop(segment + 1)
}

private val STATUS_CARD_EDGE = 32.dp

private val STATUS_CARD_WIDTH = 440.dp

@Preview(widthDp = 1280, heightDp = 720)
@Composable
internal fun DriveScreenPreview() {
  val pickup = LatLng(37.5571, 126.942)
  val plan =
    PickupPlan(
      passenger = Contact("c1", "이서연", "01000001234"),
      place = null,
      pickupPoint = PickupPoint(pickup, LocationDescription("홍대입구역 앞", "양화로 · 도로 북쪽 인도"), null),
      restriction = StopRestriction.NotFound,
      route = RoutePreview(4_000, 12 * 60, listOf(LatLng(37.5500, 126.9300), pickup)),
      vehicle = MyVehicle("흰색 아반떼", "12가 3456", "김도윤"),
    )
  val pickupState =
    PickupState(
      id = "p1",
      shareUrl = "https://way.test/p/t1",
      plan = plan,
      startedAtMillis = PREVIEW_NOW_MILLIS,
      arrivalAtMillis = PREVIEW_NOW_MILLIS + 12 * 60_000L,
      carLocation = LatLng(37.5500, 126.9300),
    )
  WayTheme {
    DriveScreen(
      state = DriveUiState(pickup = pickupState, doorAvailable = true),
      now = PREVIEW_NOW_MILLIS,
      onConfirmBoarded = {},
      onFinish = {},
      onConnectNext = {},
      onCancelNext = {},
      onCallPassenger = {},
    )
  }
}

private const val CLOCK_TICK_MILLIS = 200L
private const val RESUME_FOLLOW_MILLIS = 10_000L
private const val PREVIEW_NOW_MILLIS = 1_795_000_000_000L
