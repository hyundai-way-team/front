package com.kscho.way.presentation.map

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.geo.LatLng
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.presentation.component.theme.WayColors
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

@Composable
fun WayMap(
  center: LatLng,
  metersPerDp: Float,
  modifier: Modifier = Modifier,
  zones: List<RestrictedZone> = emptyList(),
  lines: List<MapLine> = emptyList(),
  pins: List<MapPin> = emptyList(),
  caption: String? = null,
  showZoneLegend: Boolean = false,
  interactive: Boolean = false,
  followCamera: Boolean = true,
  centerPin: MapPin? = null,
  onCenterSettled: ((LatLng) -> Unit)? = null,
  showZoomButtons: Boolean = false,
  onVisibleAreaSettled: ((LatLng, Int) -> Unit)? = null,
  cameraPadding: PaddingValues = PaddingValues(),
  resumeFollowAfterMillis: Long? = null,
) {
  if (LocalInspectionMode.current) {
    Box(modifier.background(WayColors.MapBg))
    return
  }
  val context = LocalContext.current
  val composeDensity = LocalDensity.current.density
  val layoutDirection = LocalLayoutDirection.current
  val paddingPx =
    with(LocalDensity.current) {
      listOf(
        cameraPadding.calculateLeftPadding(layoutDirection).toPx().toDouble(),
        cameraPadding.calculateTopPadding().toPx().toDouble(),
        cameraPadding.calculateRightPadding(layoutDirection).toPx().toDouble(),
        cameraPadding.calculateBottomPadding().toPx().toDouble(),
      )
    }
  val mapView = rememberMapView()
  var map by remember { mutableStateOf<MapLibreMap?>(null) }
  var style by remember { mutableStateOf<Style?>(null) }
  var cameraPlaced by remember { mutableStateOf(false) }

  DisposableEffect(mapView) {
    mapView.getMapAsync { m ->
      m.uiSettings.apply {
        isLogoEnabled = false
        isAttributionEnabled = false
        isCompassEnabled = false
        isRotateGesturesEnabled = false
        isTiltGesturesEnabled = false
      }
      m.setStyle(Style.Builder().fromUri(STYLE_URI)) { loaded ->
        addWayLayers(loaded, context)
        style = loaded
      }
      map = m
    }
    onDispose {}
  }

  LaunchedEffect(map, interactive) {
    map?.uiSettings?.apply {
      setAllGesturesEnabled(interactive)
      isRotateGesturesEnabled = false
      isTiltGesturesEnabled = false
    }
  }

  var touchedAt by remember { mutableLongStateOf(0L) }
  DisposableEffect(map, resumeFollowAfterMillis) {
    val m = map
    if (m == null || resumeFollowAfterMillis == null) return@DisposableEffect onDispose {}
    var gesture = false
    val started =
      MapLibreMap.OnCameraMoveStartedListener { reason ->
        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
          gesture = true
          touchedAt = SystemClock.elapsedRealtime()
        }
      }
    val idle =
      MapLibreMap.OnCameraIdleListener {
        if (gesture) touchedAt = SystemClock.elapsedRealtime()
        gesture = false
      }
    m.addOnCameraMoveStartedListener(started)
    m.addOnCameraIdleListener(idle)
    onDispose {
      m.removeOnCameraMoveStartedListener(started)
      m.removeOnCameraIdleListener(idle)
    }
  }

  val zoom = zoomFor(metersPerDp, center.latitude, composeDensity, context.resources.displayMetrics.density)
  LaunchedEffect(map, center, zoom, paddingPx, touchedAt) {
    val m = map ?: return@LaunchedEffect
    if (cameraPlaced && followCamera && touchedAt != 0L && resumeFollowAfterMillis != null) {
      val wait = touchedAt + resumeFollowAfterMillis - SystemClock.elapsedRealtime()
      if (wait > 0) delay(wait)
    }
    val update =
      CameraUpdateFactory.newCameraPosition(
        CameraPosition.Builder(m.cameraPosition).target(center.toMap()).zoom(zoom).padding(paddingPx.toDoubleArray()).build()
      )
    if (!cameraPlaced) {
      m.moveCamera(update)
      cameraPlaced = true
    } else if (followCamera) {
      m.easeCamera(update, 700)
    }
  }

  LaunchedEffect(style, zones) { style?.getSourceAs<GeoJsonSource>(SRC_ZONES)?.setGeoJson(zonesGeoJson(zones)) }
  LaunchedEffect(style, lines) { style?.getSourceAs<GeoJsonSource>(SRC_LINES)?.setGeoJson(linesGeoJson(lines)) }
  LaunchedEffect(style, pins) {
    val s = style ?: return@LaunchedEffect
    pins.forEach { pin ->
      val look = WayMarkers.lookOf(pin.kind) ?: return@forEach
      val key = markerKey(look, markerLabel(pin))
      if (s.getImage(key) == null) s.addImage(key, WayMarkers.draw(context, look, markerLabel(pin), composeDensity).bitmap)
    }
    s.getSourceAs<GeoJsonSource>(SRC_PINS)?.setGeoJson(pinsGeoJson(pins))
  }

  val settled by rememberUpdatedState(onCenterSettled)
  val areaSettled by rememberUpdatedState(onVisibleAreaSettled)
  DisposableEffect(map) {
    val m = map ?: return@DisposableEffect onDispose {}
    val listener =
      MapLibreMap.OnCameraIdleListener {
        val target = m.cameraPosition.target ?: return@OnCameraIdleListener
        val here = LatLng(target.latitude, target.longitude)
        settled?.invoke(here)
        if (!cameraPlaced) return@OnCameraIdleListener
        areaSettled?.let { report ->
          val r = m.projection.visibleRegion
          val corners = listOf(r.farLeft, r.farRight, r.nearLeft, r.nearRight).mapNotNull { it }
          val radius = corners.maxOfOrNull { Geo.distanceMeters(here, LatLng(it.latitude, it.longitude)) } ?: return@let
          report(here, radius.roundToInt())
        }
      }
    m.addOnCameraIdleListener(listener)
    onDispose { m.removeOnCameraIdleListener(listener) }
  }

  Box(modifier.clipToBounds().background(WayColors.MapBg)) {
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
    centerPin?.let { CenterPin(it, Modifier.align(Alignment.Center)) }
    if (caption != null) MapLabel(caption, Modifier.align(Alignment.TopStart).padding(20.dp))
    if (showZoomButtons) {
      Column(Modifier.align(Alignment.TopEnd).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ZoomButton("+") { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
        ZoomButton("−") { map?.animateCamera(CameraUpdateFactory.zoomOut()) }
      }
    }
    Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalAlignment = Alignment.End) {
      if (showZoneLegend) {
        Row(
          Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.size(width = 22.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(WayColors.Red))
          Spacer(Modifier.width(8.dp))
          Text("주정차 금지 구간", fontSize = 14.sp, color = WayColors.Ink)
        }
        Spacer(Modifier.size(6.dp))
      }
      Text(ATTRIBUTION, fontSize = 10.sp, color = WayColors.Sub, modifier = Modifier.background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 4.dp))
    }
  }
}

@Composable
internal fun rememberMapView(): MapView {
  val context = LocalContext.current
  val mapView = remember {
    MapLibre.getInstance(context)
    val options =
      MapLibreMapOptions.createFromAttributes(context)
        .textureMode(true)
        .logoEnabled(false)
        .attributionEnabled(false)
        .localIdeographFontFamily("sans-serif")
    MapView(context, options).also { it.onCreate(null) }
  }
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle, mapView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> mapView.onStart()
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        Lifecycle.Event.ON_STOP -> mapView.onStop()
        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      val state = lifecycle.currentState
      if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
      if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
      mapView.onDestroy()
    }
  }
  return mapView
}

internal fun zoomFor(metersPerDp: Float, latitude: Double, composeDensity: Float, androidDensity: Float): Double {
  val metersPerAndroidDp = metersPerDp * androidDensity / composeDensity
  val metersPerDpAtZoom0 = 40_075_016.686 * cos(Math.toRadians(latitude)) / 512.0
  return (ln(metersPerDpAtZoom0 / metersPerAndroidDp) / ln(2.0)).coerceIn(2.0, 20.0)
}

internal fun LatLng.toMap() = MapLatLng(latitude, longitude)

internal fun LatLng.point() = Point.fromLngLat(longitude, latitude)

internal fun metersToDp(meters: Double, zoom: Double): Float =
  (meters / (40_075_016.686 * cos(Math.toRadians(37.55)) / (512.0 * Math.pow(2.0, zoom)))).toFloat()
