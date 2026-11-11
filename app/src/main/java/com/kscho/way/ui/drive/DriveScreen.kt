package com.kscho.way.ui.drive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kscho.way.domain.model.BoardingSource
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.LocationSharing
import com.kscho.way.domain.model.MapFeatures
import com.kscho.way.domain.pickup.Announcement
import com.kscho.way.domain.pickup.PickupCoordinator
import com.kscho.way.domain.pickup.PickupPhase
import com.kscho.way.domain.pickup.PickupPolicy
import com.kscho.way.domain.pickup.PickupState
import com.kscho.way.domain.repository.PlaceRepository
import com.kscho.way.domain.vehicle.BoardingSensor
import com.kscho.way.pickup.PassengerCall
import com.kscho.way.theme.WayColors
import com.kscho.way.ui.common.Format
import com.kscho.way.ui.common.appContainer
import com.kscho.way.ui.map.MapLabel
import com.kscho.way.ui.map.MapLine
import com.kscho.way.ui.map.MapPin
import com.kscho.way.ui.map.PinKind
import com.kscho.way.ui.map.WayMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DriveViewModel(val coordinator: PickupCoordinator, boardingSensor: BoardingSensor, private val places: PlaceRepository) : ViewModel() {
  val state: StateFlow<PickupState?> = coordinator.state
  val doorAvailable: StateFlow<Boolean?> = boardingSensor.available
  val features = MutableStateFlow<MapFeatures?>(null)

  init {
    coordinator.state.value?.let { s ->
      val pickup = s.plan.pickupPoint.location
      // 출발 지점부터 픽업 위치까지 지도에 보이게
      val reach = s.plan.route.path.maxOfOrNull { Geo.distanceMeters(it, pickup) } ?: 0.0
      val radius = (reach + 500).coerceIn(600.0, 5_000.0).toInt()
      viewModelScope.launch { features.value = runCatching { places.mapFeatures(pickup, radius) }.getOrNull() }
    }
  }

  fun confirmBoarded() = coordinator.confirmBoarded(BoardingSource.MANUAL)

  fun close() = coordinator.finish()
}

/**
 * D-06 주행 · D-07 도착 5분 전 · D-08 정차·대기 타이머 · D-09 15초 남음·전화 연결 제안 · D-10 탑승 확인·종료.
 * 출발 뒤라 버튼은 두지 않는다 — 예외: 15초 남았을 때 전화(음성 대답의 대안), 도어를 못 쓸 때 '탑승 완료', 끝난 뒤 '닫기'.
 */
@Composable
fun DriveScreen(onClosed: () -> Unit) {
  val c = appContainer()
  val vm = viewModel { DriveViewModel(c.coordinator, c.boardingSensor, c.places) }
  val state by vm.state.collectAsStateWithLifecycle()
  val doorAvailable by vm.doorAvailable.collectAsStateWithLifecycle()
  val features by vm.features.collectAsStateWithLifecycle()
  val s = state ?: return
  val context = LocalContext.current

  // 주행·대기 중엔 뒤로 가기로 빠지지 않는다(운전자 조작 없음). 끝나면 닫는다.
  BackHandler {
    if (s.isBoarded) {
      vm.close()
      onClosed()
    }
  }

  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      now = System.currentTimeMillis()
      delay(200)
    }
  }

  val pickup = s.plan.pickupPoint.location
  val name = s.plan.passenger.callName
  Box(Modifier.fillMaxSize()) {
    val driving = s.phase == PickupPhase.DRIVING
    val car = s.carLocation
    WayMap(
      center = if (driving && car != null) car else pickup,
      metersPerDp = if (driving) 1.6f else 0.3f,
      modifier = Modifier.fillMaxSize(),
      features = features,
      lines =
        buildList {
          if (driving && car != null) add(MapLine(remainingPath(s), WayColors.Blue, 7f))
          val passenger = s.passenger.location
          if (!s.isBoarded && passenger != null) add(MapLine(listOf(passenger, pickup), WayColors.Passenger, 3f, dashed = true))
        },
      pins =
        buildList {
          add(MapPin(pickup, PinKind.PICKUP, "픽업 위치"))
          if (!s.isBoarded) {
            s.passenger.location?.let { add(MapPin(it, PinKind.PASSENGER, "$name 님" + (s.passengerDistanceMeters?.let { d -> " · ${Format.distance(d)}" } ?: ""))) }
          }
          car?.let { add(MapPin(if (driving) it else pickup, PinKind.CAR)) }
        },
    )

    StatusCard(s, Modifier.align(Alignment.TopStart).padding(32.dp).width(440.dp), onClose = { vm.close(); onClosed() })

    Column(Modifier.align(Alignment.TopEnd).padding(32.dp).width(260.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      when {
        s.isBoarded -> BoardedBadge(s)
        s.phase == PickupPhase.WAITING -> {
          val left = s.waitRemainingMillis(now) ?: 0L
          val urgent = left <= PickupPolicy.CALL_OFFER_REMAINING_SECONDS * 1000L
          WaitTimer(left, urgent)
          if (urgent) {
            Button(
              onClick = { runCatching { context.startActivity(PassengerCall.dialIntent(s.plan.passenger.phone)) } },
              modifier = Modifier.fillMaxWidth().height(56.dp),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = WayColors.Orange),
            ) {
              Text("$name 님에게 전화", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
        else -> Unit
      }
    }

    VoiceCaption(s, now, Modifier.align(Alignment.BottomStart).padding(32.dp).width(720.dp))

    if (!s.isBoarded && doorAvailable == false) {
      Column(Modifier.align(Alignment.BottomEnd).padding(32.dp), horizontalAlignment = Alignment.End) {
        OutlinedButton(onClick = vm::confirmBoarded, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(10.dp)) {
          Text("탑승 완료", fontSize = 19.sp, color = WayColors.Ink)
        }
        MapLabel("도어 정보를 쓸 수 없어 손으로 알려 주세요")
      }
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

@Composable
private fun StatusCard(s: PickupState, modifier: Modifier, onClose: () -> Unit) {
  val name = s.plan.passenger.callName
  Column(
    modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, WayColors.Line, RoundedCornerShape(16.dp)).padding(24.dp)
  ) {
    when (s.phase) {
      PickupPhase.DRIVING -> {
        Text("${s.plan.passenger.name} 님 픽업", fontSize = 16.sp, color = WayColors.Sub)
        Text(s.plan.pickupPoint.description.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WayColors.Ink)
        val remaining = s.remainingSeconds ?: s.plan.route.durationSeconds
        val meters = s.remainingMeters ?: s.plan.route.distanceMeters
        Text(
          if (remaining <= PickupPolicy.ARRIVING_SOON_SECONDS) "픽업 위치까지 ${Format.minutes(remaining)} · ${Format.distance(meters)}"
          else "픽업 위치까지 ${Format.minutes(remaining)} · ${Format.clock(s.arrivalAtMillis)} 도착 예정",
          fontSize = 18.sp,
          color = WayColors.Ink,
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = WayColors.Chip)
        Spacer(Modifier.height(14.dp))
        Checklist(s)
      }
      PickupPhase.WAITING -> {
        val urgent = s.callOffered
        Text(if (urgent) "대기 ${PickupPolicy.CALL_OFFER_REMAINING_SECONDS}초 남음 · 아직 미탑승" else "픽업 위치 도착 · 정차", fontSize = 16.sp, color = WayColors.Sub)
        Spacer(Modifier.height(6.dp))
        Text(s.plan.pickupPoint.description.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WayColors.Ink)
        Spacer(Modifier.height(8.dp))
        PassengerLine(s)
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = WayColors.Chip)
        Spacer(Modifier.height(14.dp))
        Text(
          if (urgent) "${PickupPolicy.CALL_OFFER_REMAINING_SECONDS}초 남았는데 아직 타지 않아서 음성으로 전화 연결을 제안해요. 대답하면 바로 전화를 걸어요."
          else "정차를 감지해 1분 대기 타이머를 시작했어요. $name 님 화면에도 같은 타이머가 떠요.",
          fontSize = 15.sp,
          color = WayColors.Sub,
        )
      }
      PickupPhase.BOARDED -> {
        Text("탑승 확인 · 안내 종료", fontSize = 16.sp, color = WayColors.Sub)
        Spacer(Modifier.height(6.dp))
        Text(s.plan.pickupPoint.description.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WayColors.Ink)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(14.dp).clip(CircleShape).background(WayColors.Blue))
          Spacer(Modifier.width(10.dp))
          Text("한 번에 탑승 · 픽업 위치 변경 0회", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = WayColors.Ink)
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = WayColors.Chip)
        Spacer(Modifier.height(14.dp))
        Text(
          when (s.boardingSource) {
            BoardingSource.DOOR -> "도어 열림·닫힘으로 탑승을 확인했어요. "
            else -> "탑승 완료를 확인했어요. "
          } + "위치 공유를 끝내고 링크를 만료했어요. 다음 목적지 안내는 검토 중이에요.",
          fontSize = 15.sp,
          color = WayColors.Sub,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(10.dp)) {
          Text("닫기", fontSize = 18.sp, color = WayColors.Ink)
        }
      }
    }
  }
}

@Composable
private fun Checklist(s: PickupState) {
  val name = s.plan.passenger.callName
  val link =
    when {
      !s.passenger.linkOpened -> "링크 확인 기다리는 중"
      s.passenger.sharing == LocationSharing.DECLINED -> "링크 확인 · $name 님 위치 미공유"
      else -> "링크 확인 · 서로 위치 공유 중"
    }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    CheckItem(true, "문자 보냄 · 차량 번호 · 픽업 위치 · ETA")
    CheckItem(s.passenger.linkOpened, link)
    CheckItem(s.arrivingSoonSent, if (s.arrivingSoonSent) "\"곧 도착해요\" 문자 보냄" else "도착 5분 전 \"곧 도착해요\" 문자", bold = s.arrivingSoonSent)
    if (s.etaChangeNotices > 0) CheckItem(true, "도착 시간 변경 문자 ${s.etaChangeNotices}번 보냄 (${Format.clock(s.notifiedArrivalAtMillis)})")
    CheckItem(false, "정차 · 1분 대기 · 탑승")
  }
}

@Composable
private fun CheckItem(done: Boolean, text: String, bold: Boolean = false) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier.size(24.dp).clip(CircleShape).background(if (done) WayColors.Blue else Color.White).border(2.dp, if (done) WayColors.Blue else WayColors.Line, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      if (done) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.width(12.dp))
    Text(text, fontSize = 17.sp, color = if (done) WayColors.Ink else WayColors.Sub, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
  }
}

@Composable
private fun PassengerLine(s: PickupState) {
  val name = s.plan.passenger.callName
  val text =
    when {
      s.passenger.sharing == LocationSharing.DECLINED -> "$name 님 · 위치 미공유"
      !s.passenger.linkOpened -> "$name 님 · 링크 확인 전"
      else -> {
        val d = s.passengerDistanceMeters
        when {
          d == null -> "$name 님 · 위치 확인 중"
          d <= 10 -> "$name 님 · 픽업 위치 도착"
          else -> "$name 님 · ${Format.distance(d)}" + if (s.callOffered) "" else " · 오는 중"
        }
      }
    }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(16.dp).clip(CircleShape).background(WayColors.Passenger))
    Spacer(Modifier.width(10.dp))
    // 동승자 웹 '늦어요'
    Text(if (s.passenger.late) "$text · 늦는대요" else text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WayColors.Ink)
  }
}

@Composable
private fun WaitTimer(leftMillis: Long, urgent: Boolean) {
  val color = if (urgent) WayColors.Orange else WayColors.Blue
  val total = PickupPolicy.WAIT_SECONDS * 1000f
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(2.dp, color, RoundedCornerShape(14.dp)).padding(18.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Canvas(Modifier.size(60.dp)) {
      val stroke = 7.dp.toPx()
      drawCircle(color.copy(alpha = 0.18f), radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
      drawArc(color, -90f, 360f * (leftMillis / total), useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    Spacer(Modifier.width(16.dp))
    Column {
      Text(Format.timer(leftMillis), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = color)
      Text(
        when {
          leftMillis <= 0 -> "대기 1분이 지났어요"
          urgent -> "${kotlin.math.ceil(leftMillis / 1000.0).toInt()}초 남음 · 미탑승"
          else -> "1분 대기 중"
        },
        fontSize = 15.sp,
        color = WayColors.Sub,
      )
    }
  }
}

@Composable
private fun BoardedBadge(s: PickupState) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(2.dp, WayColors.Blue, RoundedCornerShape(14.dp)).padding(18.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(56.dp).clip(CircleShape).background(WayColors.Blue), contentAlignment = Alignment.Center) {
      Text("✓", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.width(14.dp))
    Column {
      Text("탑승 완료", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = WayColors.Blue)
      Text(s.waitedSeconds?.let { "정차 ${Format.timer(it * 1000L)} 만에" } ?: "정차 전에 탑승", fontSize = 15.sp, color = WayColors.Sub)
    }
  }
}

/** 음성 안내를 화면에도 잠깐(10초) 보여 준다 — 에뮬레이터처럼 소리가 안 나도 무엇을 말했는지 보이게. */
@Composable
private fun VoiceCaption(s: PickupState, now: Long, modifier: Modifier) {
  val said = s.lastAnnouncement ?: return
  val showFor = if (said.announcement is Announcement.CallOffer) 20_000L else 10_000L
  if (now - said.atMillis > showFor) return
  Row(
    modifier.clip(RoundedCornerShape(16.dp)).background(WayColors.Dark).padding(horizontal = 24.dp, vertical = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("🔊", fontSize = 24.sp)
    Spacer(Modifier.width(16.dp))
    Column {
      Text("\"${said.announcement.text}\"", fontSize = 21.sp, color = Color.White)
      if (said.announcement is Announcement.CallOffer) {
        Text("\"응\" 또는 \"전화 걸어\"라고 말하면 전화를 걸어요", fontSize = 15.sp, color = Color(0xFFC9CED6))
      }
    }
  }
}
