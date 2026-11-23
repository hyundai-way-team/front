package com.kscho.way.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kscho.way.domain.model.place.Place
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayType

@Composable
internal fun PlaceItem(place: Place, on: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(16.dp)
  Column(
    Modifier.fillMaxWidth()
      .clip(shape)
      .then(if (on) Modifier.background(WayColors.Primary50).border(1.dp, WayColors.Primary100, shape) else Modifier)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(place.name, style = WayType.TitleM, color = WayColors.Grey900)
    Text(place.address, style = WayType.MRegular, color = WayColors.Grey500)
  }
}
