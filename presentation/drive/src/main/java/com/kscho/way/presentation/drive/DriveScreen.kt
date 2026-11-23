package com.kscho.way.presentation.drive

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kscho.way.domain.gateway.BoardingSensor
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.BoardingSource
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.domain.model.route.TurnInstruction
import com.kscho.way.domain.model.route.TurnKind
import com.kscho.way.domain.pickup.Announcement
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.pickup.PickupPhase
import com.kscho.way.domain.pickup.PickupPolicy
import com.kscho.way.domain.pickup.PickupState
import com.kscho.way.domain.usecase.restriction.GetPickupZonesUseCase
import com.kscho.way.presentation.component.common.ActionButton
import com.kscho.way.presentation.component.common.ActionKind
import com.kscho.way.presentation.component.common.Format
import com.kscho.way.presentation.component.common.RotatingText
import com.kscho.way.presentation.component.intent.PassengerCall
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayType
import com.kscho.way.presentation.drive.R
import com.kscho.way.presentation.map.MapLine
import com.kscho.way.presentation.map.MapPin
import com.kscho.way.presentation.map.PinKind
import com.kscho.way.presentation.map.WayMap
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DriveViewModel @Inject constructor(
  val coordinator: PickupCoordinator,
  boardingSensor: BoardingSensor,
  private val getPickupZones: GetPickupZonesUseCase,
) : ViewModel() {
  val state: StateFlow<PickupState?> = coordinator.state
  val doorAvailable: StateFlow<Boolean?> = boardingSensor.available
  val zones = MutableStateFlow<List<RestrictedZone>>(emptyList())

  init {
    coordinator.state.value?.let { s ->
      val pickup = s.plan.pickupPoint.location
      viewModelScope.launch {
        zones.value = runCatching { getPickupZones(pickup) }.getOrDefault(emptyList())
      }
    }
  }

  fun confirmBoarded() = coordinator.confirmBoarded(BoardingSource.MANUAL)

  fun done() = coordinator.finish()

  fun connectNext() = coordinator.connectNext()

  fun cancelNext() = coordinator.cancelNext()
}

@Composable
fun DriveScreen(onClosed: () -> Unit) {
  val vm: DriveViewModel = hiltViewModel()
  val state by vm.state.collectAsStateWithLifecycle()
  val doorAvailable by vm.doorAvailable.collectAsStateWithLifecycle()
  val zones by vm.zones.collectAsStateWithLifecycle()
  val s = state ?: return
  val context = LocalContext.current

  var closed by remember { mutableStateOf(false) }
  fun done() {
    if (closed) return
    closed = true
    vm.done()
    onClosed()
  }

  fun connect() {
    if (closed) return
    closed = true
    vm.connectNext()
    onClosed()
  }

  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      now = System.currentTimeMillis()
      delay(200)
    }
  }
  val connecting = s.connectRemainingMillis(now) != null

  BackHandler {
    when {
      connecting -> vm.cancelNext()
      s.isBoarded -> done()
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
        resumeFollowAfterMillis = 10_000L,
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
                { runCatching { context.startActivity(PassengerCall.dialIntent(s.plan.passenger.phone)) } },
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
        if (!connecting) ActionButton("탑승 완료", ::done, Modifier.align(Alignment.BottomEnd).padding(32.dp).width(180.dp))
      } else if (doorAvailable == false) {
        Column(Modifier.align(Alignment.BottomEnd).padding(32.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          MapAnnotation("도어 정보를 쓸 수 없어 손으로 알려 주세요")
          ActionButton("탑승 완료", vm::confirmBoarded, Modifier.width(180.dp))
        }
      }
    }

    if (connecting) {
      Box(Modifier.fillMaxSize().background(WayColors.Dim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {})
      NextStepDialog(s, onCancel = vm::cancelNext, onConnect = ::connect, Modifier.align(Alignment.Center))
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

private val R12 = RoundedCornerShape(12.dp)
private val R14 = RoundedCornerShape(14.dp)
private val R16 = RoundedCornerShape(16.dp)
private val R20 = RoundedCornerShape(20.dp)
private val R24 = RoundedCornerShape(24.dp)

private fun Modifier.glass(border: Color = WayColors.Grey200, borderWidth: Dp = 1.dp): Modifier =
  this.shadow(10.dp, R20, ambientColor = WayColors.ShadowMedium, spotColor = WayColors.ShadowMedium)
    .clip(R20)
    .background(Color.White.copy(alpha = 0.86f))
    .border(borderWidth, border, R20)

@Composable
private fun DrivingCard(s: PickupState) {
  val remaining = s.remainingSeconds ?: s.plan.route.durationSeconds
  val meters = s.remainingMeters ?: s.plan.route.distanceMeters
  Column(Modifier.fillMaxWidth().glass().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("${s.plan.passenger.name} 님 픽업", style = WayType.TitleL, color = WayColors.Primary500)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(s.plan.pickupPoint.description.title, style = WayType.HeadingM, color = Color.Black)
        Row(verticalAlignment = Alignment.CenterVertically) {
          RotatingText(listOf("픽업 위치까지 ${Format.minutes(remaining)}", "도착 예정 시간 ${Format.clock(s.arrivalAtMillis)}"), WayType.TitleM, WayColors.Grey500)
          Text(" | ${Format.distance(meters)}", style = WayType.TitleM, color = WayColors.Grey500)
        }
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      StepPill("픽업 정보 문자 발송", done = true)
      StepPill("실시간 위치 공유", done = s.passenger.linkOpened)
      StepPill("도착 임박 문자 발송", done = s.arrivingSoonSent)
      StepPill("정차 및 탑승", done = false)
    }
  }
}

@Composable
private fun StepPill(text: String, done: Boolean) {
  Row(
    Modifier.fillMaxWidth()
      .clip(CircleShape)
      .background(WayColors.Primary50)
      .border(1.dp, WayColors.Primary100, CircleShape)
      .padding(start = 16.dp, end = if (done) 16.dp else 8.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text, style = WayType.LMedium, color = WayColors.Grey900, modifier = Modifier.weight(1f))
    if (done) {
      Text("완료", style = WayType.MMedium, color = Color.White, modifier = Modifier.clip(CircleShape).background(WayColors.Primary500).padding(horizontal = 12.dp, vertical = 4.dp))
    } else {
      Row(
        Modifier.clip(CircleShape).background(WayColors.Primary50).padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Icon(painterResource(R.drawable.ic_dot), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
        Text("대기 중", style = WayType.MMedium, color = WayColors.Primary500)
      }
    }
  }
}

@Composable
private fun TurnCard(turn: TurnInstruction) {
  val (icon, mirrored) =
    when (turn.kind) {
      TurnKind.RIGHT, TurnKind.KEEP_RIGHT -> R.drawable.ic_turn_right to false
      TurnKind.LEFT, TurnKind.KEEP_LEFT -> R.drawable.ic_turn_right to true
      TurnKind.U_TURN -> R.drawable.ic_turn_uturn to false
      else -> R.drawable.ic_turn_straight to false
    }
  Row(
    Modifier.width(270.dp).clip(R14).background(WayColors.Grey900).padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(40.dp).scale(scaleX = if (mirrored) -1f else 1f, scaleY = 1f))
    Column(Modifier.weight(1f)) {
      Text(if (turn.kind == TurnKind.STRAIGHT) "${Format.distance(turn.distanceMeters)} ${turn.kind.label}" else "${Format.distance(turn.distanceMeters)} 앞 ${turn.kind.label}", style = WayType.TitleXL, color = Color.White)
      if (turn.road.isNotBlank()) Text(turn.road, style = WayType.LRegular, color = WayColors.Grey300, maxLines = 1)
    }
  }
}

@Composable
private fun StoppedCard(s: PickupState) {
  val name = s.plan.passenger.callName
  val car = s.carLocation
  val toPickup = car?.let { Geo.distanceMeters(it, s.plan.pickupPoint.location).roundToInt() } ?: 0
  val stoppedAt = s.arrivedAtMillis ?: s.arrivalAtMillis
  Column(Modifier.fillMaxWidth().glass().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    CardTitle(R.drawable.ic_pickup_pin, "픽업 위치 도착", s.plan.pickupPoint.description.title)
    Column(
      Modifier.fillMaxWidth().clip(R24).background(WayColors.Primary50).border(1.dp, WayColors.Primary100, R24).padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      DotLabel("픽업 위치까지")
      RotatingText(listOf(Format.distance(toPickup), "${Format.clock(stoppedAt)} 도착"), WayType.DisplayXL, WayColors.Primary500)
    }
    HorizontalDivider(color = WayColors.Grey200)
    Text(
      buildAnnotatedString {
        append("정차를 감지해 ")
        withStyle(SpanStyle(color = WayColors.Primary500)) { append("1분 대기 타이머") }
        append("를 시작했어요.\n$name 님 화면에도 같은 타이머가 떠요.")
      },
      style = WayType.LMedium,
      color = WayColors.Grey900,
    )
  }
}

@Composable
private fun PassengerRouteCard(s: PickupState) {
  val name = s.plan.passenger.callName
  val meters = s.passengerDistanceMeters ?: return
  val minutes = s.passenger.walk?.let { ceil(it.seconds / 60.0).toInt().coerceAtLeast(1) } ?: walkMinutes(meters)
  Row(Modifier.fillMaxWidth().glass().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Icon(painterResource(R.drawable.ic_check_small), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(if (s.passenger.late) "$name 님이 조금 늦는다고 알렸어요" else "$name 님이 경로를 확인했어요", style = WayType.LMedium, color = WayColors.Primary500)
      Text(if (meters <= 10) "픽업 위치에 도착했어요" else "도보 ${minutes}분 거리", style = WayType.TitleL, color = WayColors.Grey900)
    }
  }
}

private fun walkMinutes(meters: Int): Int = ceil(meters / 67.0).toInt().coerceAtLeast(1)

@Composable
private fun WaitTimerCard(leftMillis: Long, urgent: Boolean) {
  val accent = if (urgent) WayColors.Error500 else WayColors.Primary500
  Row(
    Modifier.width(if (urgent) 260.dp else 276.dp).glass(border = accent, borderWidth = 2.dp).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    ProgressRing(leftMillis / (PickupPolicy.WAIT_SECONDS * 1000f), track = if (urgent) WayColors.Error100 else WayColors.Grey200, color = accent, size = 64.dp)
    Column {
      Text(Format.timer(leftMillis), style = WayType.HeadingL, color = if (urgent) WayColors.Error600 else WayColors.Primary500)
      Text(if (urgent) "미탑승 상태" else "1분 대기 중", style = WayType.LRegular, color = WayColors.Grey500)
    }
  }
}

@Composable
private fun ProgressRing(fraction: Float, track: Color, color: Color, size: Dp, content: @Composable () -> Unit = {}) {
  Box(Modifier.size(size), contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(size)) {
      val ring = RingGeometry.of(this.size.minDimension)
      drawCircle(track, radius = ring.radius, style = Stroke(ring.stroke))
      drawArc(
        color,
        -90f,
        360f * fraction.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = center - Offset(ring.radius, ring.radius),
        size = Size(2 * ring.radius, 2 * ring.radius),
        style = Stroke(ring.stroke, cap = StrokeCap.Round),
      )
    }
    content()
  }
}

@Composable
private fun BoardedCard(s: PickupState) {
  Column(Modifier.fillMaxWidth().glass().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    CardTitle(R.drawable.ic_nav_arrow, "탑승 확인 완료", s.plan.pickupPoint.description.title)
    Column(
      Modifier.fillMaxWidth().clip(R24).background(WayColors.Primary50).border(1.dp, WayColors.Primary100, R24).padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
      DotLabel("픽업 위치 변경 0회")
    }
    HorizontalDivider(color = WayColors.Grey200)
    Text(
      when (s.boardingSource) {
        BoardingSource.DOOR -> "도어 상태로 탑승을 확인했어요."
        else -> "탑승 완료를 확인했어요."
      } + "\n위치 공유를 종료하고 참여 링크가 만료되었어요.",
      style = WayType.LMedium,
      color = WayColors.Grey900,
    )
  }
}

@Composable
private fun BoardedBadge(s: PickupState) {
  Row(
    Modifier.height(102.dp).glass(border = WayColors.Primary500, borderWidth = 2.dp).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(WayColors.Primary500), contentAlignment = Alignment.Center) {
      Icon(painterResource(R.drawable.ic_check_large), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
    }
    Column {
      Text("탑승 완료", style = WayType.HeadingM, color = WayColors.Primary500)
      Text(s.waitedSeconds?.let { "정차 후 ${waitedText(it)} 만에 탑승" } ?: "정차 전에 탑승", style = WayType.LRegular, color = WayColors.Grey500)
    }
  }
}

private fun waitedText(seconds: Int): String = if (seconds < 60) "${seconds}초" else "${seconds / 60}분 ${seconds % 60}초"

@Composable
private fun NextStepDialog(s: PickupState, onCancel: () -> Unit, onConnect: () -> Unit, modifier: Modifier) {
  var t by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) withFrameMillis { t = System.currentTimeMillis() }
  }
  val total = PickupPolicy.CONNECT_AFTER_BOARDING_SECONDS * 1000L
  val left = s.connectRemainingMillis(t) ?: 0L
  val seconds = ceil(left / 1000.0).toInt()
  val destination = s.plan.destination
  Column(
    modifier
      .width(640.dp)
      .shadow(10.dp, R24, ambientColor = WayColors.Grey900.copy(alpha = 0.3f), spotColor = WayColors.Grey900.copy(alpha = 0.3f))
      .clip(R24)
      .background(Color.White)
      .border(1.dp, WayColors.Grey200, R24)
      .padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
      ProgressRing(left / total.toFloat(), track = WayColors.Grey200, color = WayColors.Primary500, size = 96.dp) {
        Text("$seconds", style = WayType.HeadingL, color = WayColors.Countdown)
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(painterResource(R.drawable.ic_check_small), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
          Text(if (destination != null) "탑승 확인 완료" else "픽업 안내 종료", style = WayType.LRegular, color = WayColors.Grey500)
        }
        Text(
          if (destination != null) "${seconds}초 뒤 안내가 시작돼요" else "${seconds}초 뒤 기본 내비로 연결해요",
          style = WayType.HeadingM,
          color = WayColors.DialogTitle,
        )
      }
    }
    if (destination != null) {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
          Modifier.fillMaxWidth().clip(R20).border(1.dp, WayColors.Grey300, R20).padding(horizontal = 20.dp, vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          DialogIcon(R.drawable.ic_nav_fill, WayColors.Primary500)
          Column {
            Text("픽업 후 목적지", style = WayType.LMedium, color = WayColors.Grey500)
            Text(destination.name, style = WayType.TitleXL, color = WayColors.Grey900, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
        val arrivalAt = s.destinationArrivalAtMillis
        val trip = s.destinationTripSeconds
        if (arrivalAt != null && trip != null) {
          Text(
            "${Format.clock(arrivalAt)} 도착 예정 (약 ${Format.minutes(trip)})",
            style = WayType.TitleXL,
            color = WayColors.Primary500,
            modifier =
              Modifier.fillMaxWidth().clip(R20).background(WayColors.Primary50).border(1.dp, WayColors.Primary100, R20).padding(horizontal = 20.dp, vertical = 16.dp),
          )
        }
      }
    } else {
      Row(
        Modifier.fillMaxWidth().clip(R20).background(WayColors.Grey100).border(1.dp, WayColors.Grey200, R20).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        DialogIcon(R.drawable.ic_mic, WayColors.Grey500)
        Column {
          Text("입력한 목적지가 없어요", style = WayType.TitleXL, color = WayColors.Grey900)
          Text("내비에서 음성으로 목적지를 설정할 수 있어요.", style = WayType.LMedium, color = WayColors.Grey500)
        }
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      ActionButton("취소", onCancel, Modifier.weight(1f), kind = ActionKind.OUTLINE)
      ActionButton(if (destination != null) "안내 시작" else "지금 연결", onConnect, Modifier.weight(1f))
    }
  }
}

@Composable
private fun DialogIcon(@DrawableRes icon: Int, background: Color) {
  Box(Modifier.size(48.dp).clip(R12).background(background), contentAlignment = Alignment.Center) {
    Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
  }
}

@Composable
private fun CardTitle(@DrawableRes icon: Int, title: String, subtitle: String) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
      Text(title, style = WayType.TitleL, color = WayColors.Grey900)
    }
    Text(subtitle, style = WayType.LMedium, color = WayColors.Grey500)
  }
}

@Composable
private fun DotLabel(text: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Icon(painterResource(R.drawable.ic_dot), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
    Text(text, style = WayType.TitleM, color = WayColors.Grey500)
  }
}

@Composable
private fun MapAnnotation(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = WayType.MRegular,
    color = WayColors.Grey600,
    modifier =
      modifier
        .shadow(5.dp, CircleShape, ambientColor = WayColors.ShadowMedium, spotColor = WayColors.ShadowMedium)
        .clip(CircleShape)
        .background(Color.White)
        .border(1.dp, WayColors.Grey200, CircleShape)
        .padding(horizontal = 12.dp, vertical = 6.dp),
  )
}

@Composable
private fun VoiceBanner(s: PickupState, now: Long, modifier: Modifier) {
  val said = s.lastAnnouncement ?: return
  val offer = said.announcement is Announcement.CallOffer
  if (now - said.atMillis > if (offer) 20_000L else 10_000L) return
  Row(
    modifier.clip(R16).background(WayColors.Grey900).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = if (offer) Alignment.Top else Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(painterResource(R.drawable.ic_voice), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("“${said.announcement.text}”", style = WayType.TitleM, color = Color.White)
      if (offer) Text("“응” 또는 “전화 걸어”라고 말하면 전화를 걸어요", style = WayType.LRegular, color = WayColors.Grey300)
    }
  }
}
