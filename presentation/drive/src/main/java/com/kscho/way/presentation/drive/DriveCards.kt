package com.kscho.way.presentation.drive

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.pickup.BoardingSource
import com.kscho.way.domain.model.route.TurnInstruction
import com.kscho.way.domain.model.route.TurnKind
import com.kscho.way.domain.pickup.PickupPolicy
import com.kscho.way.domain.pickup.PickupState
import com.kscho.way.presentation.component.common.Format
import com.kscho.way.presentation.component.common.RotatingText
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayType
import com.kscho.way.presentation.drive.R
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun DrivingCard(s: PickupState) {
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
internal fun StepPill(text: String, done: Boolean) {
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
internal fun TurnCard(turn: TurnInstruction) {
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
internal fun StoppedCard(s: PickupState) {
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
internal fun PassengerRouteCard(s: PickupState) {
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

internal fun walkMinutes(meters: Int): Int = ceil(meters / 67.0).toInt().coerceAtLeast(1)

@Composable
internal fun WaitTimerCard(leftMillis: Long, urgent: Boolean) {
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
internal fun ProgressRing(fraction: Float, track: Color, color: Color, size: Dp, content: @Composable () -> Unit = {}) {
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
internal fun BoardedCard(s: PickupState) {
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
internal fun BoardedBadge(s: PickupState) {
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

internal fun waitedText(seconds: Int): String = if (seconds < 60) "${seconds}초" else "${seconds / 60}분 ${seconds % 60}초"

@Composable
internal fun CardTitle(@DrawableRes icon: Int, title: String, subtitle: String) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
      Text(title, style = WayType.TitleL, color = WayColors.Grey900)
    }
    Text(subtitle, style = WayType.LMedium, color = WayColors.Grey500)
  }
}

@Composable
internal fun DotLabel(text: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Icon(painterResource(R.drawable.ic_dot), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
    Text(text, style = WayType.TitleM, color = WayColors.Grey500)
  }
}
