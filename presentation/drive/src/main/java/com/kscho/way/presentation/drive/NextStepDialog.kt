package com.kscho.way.presentation.drive

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kscho.way.domain.pickup.PickupPolicy
import com.kscho.way.domain.pickup.PickupState
import com.kscho.way.presentation.component.common.ActionButton
import com.kscho.way.presentation.component.common.ActionKind
import com.kscho.way.presentation.component.common.Format
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayType
import com.kscho.way.presentation.drive.R
import kotlin.math.ceil

@Composable
internal fun NextStepDialog(s: PickupState, onCancel: () -> Unit, onConnect: () -> Unit, modifier: Modifier) {
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
internal fun DialogIcon(@DrawableRes icon: Int, background: Color) {
  Box(Modifier.size(48.dp).clip(R12).background(background), contentAlignment = Alignment.Center) {
    Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
  }
}
