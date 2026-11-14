package com.kscho.way.ui.drive

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.R
import com.kscho.way.domain.model.BoardingSource
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.RestrictedZone
import com.kscho.way.domain.model.TurnInstruction
import com.kscho.way.domain.model.TurnKind
import com.kscho.way.domain.pickup.Announcement
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.pickup.PickupPhase
import com.kscho.way.domain.pickup.PickupPolicy
import com.kscho.way.domain.pickup.PickupState
import com.kscho.way.domain.repository.StopRestrictionRepository
import com.kscho.way.domain.vehicle.BoardingSensor
import com.kscho.way.pickup.PassengerCall
import com.kscho.way.theme.WayColors
import com.kscho.way.theme.WayType
import com.kscho.way.ui.common.ActionButton
import com.kscho.way.ui.common.ActionKind
import com.kscho.way.ui.common.Format
import com.kscho.way.ui.common.RotatingText
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.map.MapLine
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DriveViewModel(val coordinator: PickupCoordinator, boardingSensor: BoardingSensor, private val restrictions: StopRestrictionRepository) : ViewModel() {
  val state: StateFlow<PickupState?> = coordinator.state
  val doorAvailable: StateFlow<Boolean?> = boardingSensor.available
  val zones = MutableStateFlow<List<RestrictedZone>>(emptyList())

  init {
    coordinator.state.value?.let { s ->
      // 정차 금지 구간은 픽업 위치 둘레만 — 오는 길 곳곳의 구간은 운전자에게 필요 없다
      val pickup = s.plan.pickupPoint.location
      viewModelScope.launch { zones.value = runCatching { restrictions.zonesNear(pickup, PICKUP_ZONE_RADIUS_METERS) }.getOrDefault(emptyList()) }
    }
  }

  private companion object {
    /** 정차 화면(D-08, 1dp = 0.3 m)에서 보이는 가장 먼 모서리쯤. */
    const val PICKUP_ZONE_RADIUS_METERS = 300
  }

  fun confirmBoarded() = coordinator.confirmBoarded(BoardingSource.MANUAL)

  /** D-10 '탑승 완료'(D-11 에서 '취소'한 뒤) · 뒤로 가기: 픽업 화면을 닫는다. */
  fun done() = coordinator.finish()

  /** D-11 '안내 시작' · '지금 연결': 목적지 안내(또는 기본 내비)로 바로 잇고 닫는다. */
  fun connectNext() = coordinator.connectNext()

  /** D-11 '취소': 잇지 않고 D-10 에 남는다. */
  fun cancelNext() = coordinator.cancelNext()
}

/**
 * D-06 주행 · D-07 도착 5분 전 · D-08 정차·대기 타이머(D-08a 동승자 경로) · D-09 15초 남음·전화 제안 · D-10 탑승 확인(와이어프레임 v2).
 * 출발 뒤라 버튼은 두지 않는다 — 예외: 15초 남았을 때 '전화하기', 도어를 못 쓸 때 '탑승 완료', 탑승 뒤 '탑승 완료'(목적지 안내로).
 */
@Composable
fun DriveScreen(onClosed: () -> Unit) {
  val c = appContainer()
  val vm = viewModel { DriveViewModel(c.coordinator, c.boardingSensor, c.restrictions) }
  val state by vm.state.collectAsStateWithLifecycle()
  val doorAvailable by vm.doorAvailable.collectAsStateWithLifecycle()
  val zones by vm.zones.collectAsStateWithLifecycle()
  val s = state ?: return
  val context = LocalContext.current

  var closed by remember { mutableStateOf(false) }
  fun done() {
    if (closed) return // 버튼 · 뒤로 가기가 겹쳐도 한 번만
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
  // D-11: 탑승 뒤 5초 동안 다음 목적지 연결 창(남은 시간은 PickupCoordinator 가 세는 것과 같은 탑승 시각 기준)
  val connecting = s.connectRemainingMillis(now) != null

  // 주행·대기 중엔 뒤로 가기로 빠지지 않는다(운전자 조작 없음). D-11 에선 '취소', D-10 에선 닫기.
  BackHandler {
    when {
      connecting -> vm.cancelNext()
      s.isBoarded -> done()
    }
  }

  val pickup = s.plan.pickupPoint.location
  val name = s.plan.passenger.callName
  Box(Modifier.fillMaxSize()) {
    // D-11 창이 뜨면 뒤(지도·카드)는 흐리게
    BoxWithConstraints(Modifier.fillMaxSize().then(if (connecting) Modifier.blur(2.dp) else Modifier)) {
      val driving = s.phase == PickupPhase.DRIVING
      val car = s.carLocation
      // 주행 중엔 차를 왼쪽 상태 카드에 안 가린 곳의 가운데에 두고, 픽업 위치에 가까워질수록 확대(DriveCamera)
      val covered = STATUS_CARD_EDGE + STATUS_CARD_WIDTH
      WayMap(
        center = if (driving && car != null) car else pickup,
        metersPerDp =
          when {
            !driving -> DriveCamera.NEAR_METERS_PER_DP
            car == null -> DriveCamera.FAR_METERS_PER_DP
            else -> DriveCamera.metersPerDp(Geo.distanceMeters(car, pickup), maxWidth.value, maxHeight.value, covered.value)
          },
        modifier = Modifier.fillMaxSize(),
        cameraPadding = if (driving) PaddingValues(start = covered) else PaddingValues(),
        // 탄 뒤엔 정차 금지 구간·픽업 핀 없이 지금 차 자리만(D-10)
        zones = if (s.isBoarded) emptyList() else zones,
        lines =
          buildList {
            if (driving && car != null) add(MapLine(remainingPath(s), WayColors.Blue, 7f))
            val passenger = s.passenger.location
            // 동승자 걷는 길 — 서버가 지도 도로선으로 찾은 것(동승자 웹 P-05 와 같은 경로). 아직 없으면 곧은 점선
            val walk = s.passenger.walk
            if (!s.isBoarded && walk != null) add(MapLine(walk.path, WayColors.Passenger, 5f))
            else if (!s.isBoarded && passenger != null) add(MapLine(listOf(passenger, pickup), WayColors.Passenger, 3f, dashed = true))
          },
        pins =
          buildList {
            if (s.isBoarded) {
              // 탄 자리 = 픽업 위치(정차 중 차 위치는 늦게 올 수 있어 픽업 위치에 둔다)
              add(MapPin(pickup, PinKind.CAR, "현재 위치"))
              return@buildList
            }
            add(MapPin(pickup, PinKind.PICKUP, "픽업 위치"))
            s.passenger.location?.let { add(MapPin(it, PinKind.PASSENGER, "$name 님" + (s.passengerDistanceMeters?.let { d -> " · ${Format.distance(d)}" } ?: ""))) }
            car?.let { add(MapPin(if (driving) it else pickup, PinKind.CAR)) }
          },
      )

      // 왼쪽 위: 픽업 상태
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

      // 오른쪽 위: 지도 설명 · 회전 안내 / 대기 타이머 / 탑승 완료
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

      // 오른쪽 아래
      if (s.isBoarded) {
        if (!connecting) ActionButton("탑승 완료", ::done, Modifier.align(Alignment.BottomEnd).padding(32.dp).width(180.dp))
      } else if (doorAvailable == false) {
        Column(Modifier.align(Alignment.BottomEnd).padding(32.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          MapAnnotation("도어 정보를 쓸 수 없어 손으로 알려 주세요")
          ActionButton("탑승 완료", vm::confirmBoarded, Modifier.width(180.dp))
        }
      }
    }

    // D-11 다음 목적지 연결
    if (connecting) {
      // 뒤를 가리고 누름도 막는다
      Box(Modifier.fillMaxSize().background(WayColors.Dim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {})
      NextStepDialog(s, onCancel = vm::cancelNext, onConnect = ::connect, Modifier.align(Alignment.Center))
      VoiceBanner(s, now, Modifier.align(Alignment.BottomCenter).padding(32.dp).fillMaxWidth())
    }
  }
}

/** 지도에 그릴 남은 경로(차 → 미리보기 경로의 남은 부분 → 픽업 위치). */
private fun remainingPath(s: PickupState): List<LatLng> {
  val car = s.carLocation ?: return emptyList()
  val path = s.plan.route.path
  if (path.size < 2) return listOf(car, s.plan.pickupPoint.location)
  // 차가 올라탄 구간(가장 가까운 선분) 다음 꼭짓점부터
  val segment = (0 until path.size - 1).minBy { Geo.distanceToPathMeters(car, listOf(path[it], path[it + 1])) }
  return listOf(car) + path.drop(segment + 1)
}

/** 왼쪽 위 상태 카드 자리(화면 가장자리 여백 · 너비) — 주행 지도는 이 오른쪽을 본다. */
private val STATUS_CARD_EDGE = 32.dp
private val STATUS_CARD_WIDTH = 440.dp

private val R12 = RoundedCornerShape(12.dp)
private val R14 = RoundedCornerShape(14.dp)
private val R16 = RoundedCornerShape(16.dp)
private val R20 = RoundedCornerShape(20.dp)
private val R24 = RoundedCornerShape(24.dp)

/** 지도 위 반투명 카드(흰 86% · Grey-200 테두리 · 그림자 Grey-Medium 20 · 라운드 20). */
private fun Modifier.glass(border: Color = WayColors.Grey200, borderWidth: Dp = 1.dp): Modifier =
  this.shadow(10.dp, R20, ambientColor = WayColors.ShadowMedium, spotColor = WayColors.ShadowMedium)
    .clip(R20)
    .background(Color.White.copy(alpha = 0.86f))
    .border(borderWidth, border, R20)

// ── D-06 · D-07 주행

@Composable
private fun DrivingCard(s: PickupState) {
  val remaining = s.remainingSeconds ?: s.plan.route.durationSeconds
  val meters = s.remainingMeters ?: s.plan.route.distanceMeters
  Column(Modifier.fillMaxWidth().glass().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(WayColors.Grey200).border(2.4.dp, WayColors.Primary500, CircleShape))
        Text("${s.plan.passenger.name} 님 픽업", style = WayType.TitleL, color = WayColors.Primary500)
      }
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(s.plan.pickupPoint.description.title, style = WayType.HeadingM, color = Color.Black)
        // '[픽업 위치까지 12분 ↔ 도착 예정 시간 09:50] | 1.1km' — 앞부분이 5초마다 굴러 바뀌고, 남은 거리는 그대로
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

/** 진행 한 줄(Primary-50 바탕 알약) — 끝났으면 '완료', 아니면 '● 대기 중'. */
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

/** 다음 회전 안내(Pleos 내비 TBT) — '300m 앞 우회전 · 성산로'. */
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

// ── D-08 · D-09 정차 · 대기

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
      // '[30m ↔ 09:50 도착]' — 5초마다 굴러 바뀐다(정차 뒤라 실제 정차 시각)
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

/** D-08a · D-09: 동승자가 위치를 공유하면 '서연 님이 경로를 확인했어요 · 도보 2분 거리'. */
@Composable
private fun PassengerRouteCard(s: PickupState) {
  val name = s.plan.passenger.callName
  val meters = s.passengerDistanceMeters ?: return
  // 걷는 길을 찾았으면 그 시간(동승자 화면의 '도보 N분'과 같다)
  val minutes = s.passenger.walk?.let { ceil(it.seconds / 60.0).toInt().coerceAtLeast(1) } ?: walkMinutes(meters)
  Row(Modifier.fillMaxWidth().glass().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Icon(painterResource(R.drawable.ic_check_small), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(if (s.passenger.late) "$name 님이 조금 늦는다고 알렸어요" else "$name 님이 경로를 확인했어요", style = WayType.LMedium, color = WayColors.Primary500)
      Text(if (meters <= 10) "픽업 위치에 도착했어요" else "도보 ${minutes}분 거리", style = WayType.TitleL, color = WayColors.Grey900)
    }
  }
}

/** 걷는 시간(분) — 시속 4km(분당 약 67m), 최소 1분. */
private fun walkMinutes(meters: Int): Int = ceil(meters / 67.0).toInt().coerceAtLeast(1)

/** 1분 대기 타이머(D-08) · 15초 남음(D-09, 빨강). */
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

/** 둥근 진행 고리(바탕 고리 위에 남은 만큼 호). */
@Composable
private fun ProgressRing(fraction: Float, track: Color, color: Color, size: Dp, content: @Composable () -> Unit = {}) {
  Box(Modifier.size(size), contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(size)) {
      val stroke = this.size.minDimension * 0.1f
      drawCircle(track, radius = this.size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
      drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    content()
  }
}

// ── D-10 탑승 확인

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

/**
 * D-11 다음 목적지 연결(와이어프레임 v2) — 탑승하면 5초를 세고, 정해 둔 목적지가 있으면 그곳 안내를 Pleos 내비에 넘기고
 * 없으면 기본 내비를 띄운다. 5초는 PickupCoordinator 가 탑승 시각부터 센다(Way 가 뒤에 있어도 넘어가게) — 여기선 보여 주기만.
 */
@Composable
private fun NextStepDialog(s: PickupState, onCancel: () -> Unit, onConnect: () -> Unit, modifier: Modifier) {
  // 고리가 매끄럽게 줄도록 프레임마다
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
        // 내비가 답하기 전(보통 1초 안)이나 답하지 않으면 이 줄은 뺀다
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

/** D-11 상자 왼쪽 아이콘(48 · 라운드 12 · 흰 아이콘 24). */
@Composable
private fun DialogIcon(@DrawableRes icon: Int, background: Color) {
  Box(Modifier.size(48.dp).clip(R12).background(background), contentAlignment = Alignment.Center) {
    Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
  }
}

// ── 공통

/** 카드 머리: 아이콘 32 + 제목(Title/L) · 아래 픽업 위치(L/Medium · Grey-500). */
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

/** '● 픽업 위치까지' 같은 작은 머리(점 24 + Title/M · Grey-500). */
@Composable
private fun DotLabel(text: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Icon(painterResource(R.drawable.ic_dot), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
    Text(text, style = WayType.TitleM, color = WayColors.Grey500)
  }
}

/** 지도 위 작은 설명(흰 알약 · Grey-200 테두리 · M/Regular · Grey-600). */
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

/** 음성 안내를 화면에도 잠깐(10초, 전화 제안은 20초) — 소리가 안 나도 무엇을 말했는지 보이게. */
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
