package com.kscho.way.presentation.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kscho.way.domain.pickup.Announcement
import com.kscho.way.domain.pickup.PickupState
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayType
import com.kscho.way.presentation.drive.R

@Composable
internal fun MapAnnotation(text: String, modifier: Modifier = Modifier) {
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
internal fun VoiceBanner(s: PickupState, now: Long, modifier: Modifier) {
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
