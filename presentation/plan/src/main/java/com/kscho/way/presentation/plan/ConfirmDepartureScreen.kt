package com.kscho.way.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kscho.way.domain.model.contact.Contact
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.pickup.PickupPoint
import com.kscho.way.domain.model.pickup.PickupPointCheck
import com.kscho.way.domain.model.place.LocationDescription
import com.kscho.way.domain.model.place.Place
import com.kscho.way.domain.model.restriction.StopRestriction
import com.kscho.way.domain.model.route.RoutePreview
import com.kscho.way.domain.model.vehicle.MyVehicle
import com.kscho.way.presentation.component.base.CollectUiEvents
import com.kscho.way.presentation.component.common.ActionButton
import com.kscho.way.presentation.component.common.AlertBox
import com.kscho.way.presentation.component.common.BackButton
import com.kscho.way.presentation.component.common.Format
import com.kscho.way.presentation.component.common.MapFrame
import com.kscho.way.presentation.component.common.Panel
import com.kscho.way.presentation.component.common.ScreenFrame
import com.kscho.way.presentation.component.common.StepDots
import com.kscho.way.presentation.component.common.Tone
import com.kscho.way.presentation.component.theme.WayColors
import com.kscho.way.presentation.component.theme.WayTheme
import com.kscho.way.presentation.component.theme.WayType
import com.kscho.way.presentation.map.MapLine
import com.kscho.way.presentation.map.MapPin
import com.kscho.way.presentation.map.PinKind
import com.kscho.way.presentation.map.WayMap
import com.kscho.way.presentation.plan.R
import kotlin.math.max

@Composable
fun ConfirmDepartureRoute(
  onBack: () -> Unit,
  onPickupStarted: () -> Unit,
  viewModel: ConfirmDepartureViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  CollectUiEvents(viewModel.uiEvent) { event ->
    when (event) {
      ConfirmDepartureEvent.NavigateBack -> onBack()
      ConfirmDepartureEvent.PickupStarted -> onPickupStarted()
    }
  }
  ConfirmDepartureScreen(
    state = state,
    onBack = onBack,
    onClearDestination = viewModel::clearDestination,
    onStart = viewModel::start,
  )
}

@Composable
fun ConfirmDepartureScreen(
  state: ConfirmDepartureUiState,
  onBack: () -> Unit,
  onClearDestination: () -> Unit,
  onStart: () -> Unit,
) {
  val passenger = state.passenger ?: return
  val check = state.check ?: return
  val arrival = state.arrivalAtMillis

  ScreenFrame {
    Panel(width = 560.dp) {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(Modifier.fillMaxWidth()) {
          BackButton(onBack)
          Box(Modifier.align(Alignment.CenterEnd)) { StepDots(step = 3, lineWidth = 40.dp) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
          Text("이대로 출발할까요?", style = WayType.HeadingM, color = Color.Black)
          Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(
              Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, WayColors.Primary500, RoundedCornerShape(16.dp)).padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              InfoRow("동승자") { Text("${passenger.name} (${passenger.displayMaskedPhone})", style = WayType.TitleM, color = WayColors.Grey900) }
              HorizontalDivider(color = WayColors.Grey200)
              InfoRow("픽업 위치") { Text(check.point.description.title, style = WayType.TitleM, color = WayColors.Grey900) }
              HorizontalDivider(color = WayColors.Grey200)
              InfoRow("도착 예정") {
                Text("${Format.clock(arrival)} (${Format.duration(check.route.durationSeconds)})", style = WayType.TitleM, color = WayColors.Primary500)
              }
              state.vehicle?.let { v ->
                HorizontalDivider(color = WayColors.Grey200)
                InfoRow("내 차") { Text("${v.description} | ${v.plateNumber}", style = WayType.TitleM, color = WayColors.Grey900) }
              }
              state.destination?.let { d ->
                HorizontalDivider(color = WayColors.Grey200)
                InfoRow("목적지") {
                  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name, style = WayType.TitleM, color = WayColors.Grey900, modifier = Modifier.weight(1f))
                    Icon(
                      painterResource(R.drawable.ic_close),
                      contentDescription = "목적지 지우기",
                      tint = Color.Unspecified,
                      modifier = Modifier.size(24.dp).clickable(role = Role.Button, onClick = onClearDestination),
                    )
                  }
                }
              }
            }
            state.error?.let { AlertBox(it, Tone.ERROR) }
          }
        }
      }
      ActionButton(
        if (state.starting) "출발 준비 중…" else "출발",
        onStart,
        Modifier.fillMaxWidth().padding(top = 16.dp),
        enabled = state.vehicle != null && !state.starting,
      )
    }
    MapFrame {
      RoutePreviewMap(check.route.path.ifEmpty { listOf(check.point.location) }, check.point.location, Modifier.fillMaxSize())
    }
  }
}

@Composable
private fun InfoRow(label: String, content: @Composable () -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
    Text(label, style = WayType.LMedium, color = WayColors.Grey500, modifier = Modifier.width(66.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
  }
}

@Composable
private fun RoutePreviewMap(path: List<LatLng>, pickup: LatLng, modifier: Modifier) {
  BoxWithConstraints(modifier) {
    val lats = path.map { it.latitude } + pickup.latitude
    val lngs = path.map { it.longitude } + pickup.longitude
    val center = LatLng((lats.min() + lats.max()) / 2, (lngs.min() + lngs.max()) / 2)
    val (width, height) = Geo.offsetMeters(LatLng(lats.min(), lngs.min()), LatLng(lats.max(), lngs.max()))
    val metersPerDp = max(max(width / (maxWidth.value * 0.75), height / (maxHeight.value * 0.75)), 0.3).toFloat()
    WayMap(
      center = center,
      metersPerDp = metersPerDp,
      modifier = Modifier.fillMaxSize(),
      lines = listOf(MapLine(path, WayColors.Blue, 7f)),
      pins = listOfNotNull(path.firstOrNull()?.takeIf { path.size > 1 }?.let { MapPin(it, PinKind.CAR, "내 차 · 출발 지점") }, MapPin(pickup, PinKind.PICKUP, "픽업 위치")),
      caption = "지도 · 픽업 위치까지 경로 미리보기",
      interactive = true,
    )
  }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
internal fun ConfirmDepartureScreenPreview() {
  val pickup = LatLng(37.5571, 126.942)
  val check =
    PickupPointCheck(
      point = PickupPoint(pickup, LocationDescription("홍대입구역 앞", "양화로 · 도로 북쪽 인도"), null),
      route = RoutePreview(4_000, 12 * 60, listOf(LatLng(37.5500, 126.9300), pickup)),
      arrivalAtMillis = PREVIEW_ARRIVAL_MILLIS,
      restriction = StopRestriction.NotFound,
    )
  WayTheme {
    ConfirmDepartureScreen(
      state =
        ConfirmDepartureUiState(
          passenger = Contact("c1", "이서연", "01000001234"),
          check = check,
          arrivalAtMillis = PREVIEW_ARRIVAL_MILLIS,
          destination = Place("d1", "공덕역", "서울 마포구 마포대로 지하 92", LatLng(37.5443, 126.9516)),
          vehicle = MyVehicle("흰색 아반떼", "12가 3456", "김도윤"),
        ),
      onBack = {},
      onClearDestination = {},
      onStart = {},
    )
  }
}

private const val PREVIEW_ARRIVAL_MILLIS = 1_795_000_000_000L
