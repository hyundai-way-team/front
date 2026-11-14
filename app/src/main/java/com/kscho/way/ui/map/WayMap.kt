package com.kscho.way.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kscho.way.domain.model.Geo
import com.kscho.way.domain.model.LatLng
import com.kscho.way.domain.model.RestrictedZone
import com.kscho.way.theme.WayColors
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

enum class PinKind {
  CAR,
  /** 픽업 위치(확정·조정 중인 핀) */
  PICKUP,
  /** 정차 금지 구간에 놓인 핀 */
  PICKUP_BLOCKED,
  /** 검색한 약속 장소 */
  PLACE,
  /** 처음 핀(약속 장소) · 이전 핀 — 흐리게 */
  GHOST,
  GHOST_BLOCKED,
  PASSENGER,
}

data class MapPin(val location: LatLng, val kind: PinKind, val label: String? = null)

data class MapLine(val path: List<LatLng>, val color: Color, val widthDp: Float = 6f, val dashed: Boolean = false)

/** 앱에 넣은 오프라인 지도(tools/map/build_offline_map.py). 실행 중 외부 지도 API 를 부르지 않는다. */
private const val STYLE_URI = "asset://map/style.json"
private const val ATTRIBUTION = "© OpenMapTiles © OpenStreetMap contributors"

private const val SRC_ZONES = "way-zones"
private const val SRC_LINES = "way-lines"
private const val SRC_PINS = "way-pins"

/**
 * Way 지도. MapLibre 로 실제 도로 지도를 그리고, 그 위에 정차 금지 구간 · 경로 · 핀을 얹는다.
 *
 * @param center 카메라 중심. [followCamera] 면 바뀔 때마다 따라가고, 아니면 처음 한 번만 맞춘다.
 * @param metersPerDp 확대 정도(화면 1dp 가 몇 m 인지).
 * @param centerPin 화면 한가운데 고정한 핀(지도를 움직여 맞춘다 — D-04). 위치는 [onCenterSettled] 로 알려 준다.
 */
@Composable
fun WayMap(
  center: LatLng,
  metersPerDp: Float,
  modifier: Modifier = Modifier,
  /** 정차 금지 구간(서버 데이터) — 빨간 선. 도로·장소는 오프라인 지도에 있다. */
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
  /** 지도가 멈출 때마다 화면 가운데와 보이는 반지름(m, 가운데 → 가장 먼 모서리) — 보이는 곳의 정차 금지 구간을 다시 받게. */
  onVisibleAreaSettled: ((LatLng, Int) -> Unit)? = null,
) {
  val context = LocalContext.current
  val composeDensity = LocalDensity.current.density
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

  LaunchedEffect(map, interactive) { map?.uiSettings?.setAllGesturesEnabled(interactive) }

  // 카메라: 처음엔 바로, 이후엔 따라가기면 부드럽게
  val zoom = zoomFor(metersPerDp, center.latitude, composeDensity, context.resources.displayMetrics.density)
  LaunchedEffect(map, center, zoom) {
    val m = map ?: return@LaunchedEffect
    val update = CameraUpdateFactory.newLatLngZoom(center.toMap(), zoom)
    if (!cameraPlaced) {
      m.moveCamera(update)
      cameraPlaced = true
    } else if (followCamera) {
      m.easeCamera(update, 700)
    }
  }

  LaunchedEffect(style, zones) { style?.getSourceAs<GeoJsonSource>(SRC_ZONES)?.setGeoJson(zonesGeoJson(zones)) }
  LaunchedEffect(style, lines) { style?.getSourceAs<GeoJsonSource>(SRC_LINES)?.setGeoJson(linesGeoJson(lines)) }
  LaunchedEffect(style, pins) { style?.getSourceAs<GeoJsonSource>(SRC_PINS)?.setGeoJson(pinsGeoJson(pins)) }

  val settled by rememberUpdatedState(onCenterSettled)
  val areaSettled by rememberUpdatedState(onVisibleAreaSettled)
  DisposableEffect(map) {
    val m = map ?: return@DisposableEffect onDispose {}
    val listener =
      MapLibreMap.OnCameraIdleListener {
        val target = m.cameraPosition.target ?: return@OnCameraIdleListener
        val here = LatLng(target.latitude, target.longitude)
        settled?.invoke(here)
        // 처음 자리를 잡기 전(바다 한가운데 0,0) 멈춤은 알리지 않는다
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
      // OpenStreetMap 데이터 출처 표기(ODbL)
      Text(ATTRIBUTION, fontSize = 10.sp, color = WayColors.Sub, modifier = Modifier.background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 4.dp))
    }
  }
}

@Composable
fun MapLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    fontSize = 14.sp,
    color = WayColors.Sub,
    modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp),
  )
}

@Composable
private fun ZoomButton(text: String, onClick: () -> Unit) {
  Box(
    Modifier.size(48.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(Color.White)
      .border(1.dp, WayColors.Line, RoundedCornerShape(8.dp))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, fontSize = 24.sp, color = WayColors.Ink)
  }
}

/** 화면 가운데 고정 핀 — 뾰족한 끝이 정확히 지도 중심을 가리킨다. */
@Composable
private fun CenterPin(pin: MapPin, modifier: Modifier) {
  val color = pinColor(pin.kind)
  // 크기가 0인 점에 두고 그 점을 기준으로 그린다 — 이름표 길이가 핀 위치를 밀지 않게
  Box(modifier.anchorAt(0.dp, 0.dp)) {
    Canvas(Modifier.anchorAt((-20).dp, (-56).dp).size(40.dp, 56.dp)) {
      val r = 18.dp.toPx()
      val tip = Offset(size.width / 2, size.height)
      val head = Offset(size.width / 2, r + 2.dp.toPx())
      drawPath(
        Path().apply {
          moveTo(tip.x, tip.y)
          lineTo(head.x - r * 0.75f, head.y + r * 0.6f)
          lineTo(head.x + r * 0.75f, head.y + r * 0.6f)
          close()
        },
        color,
      )
      drawCircle(color, r, head)
      drawCircle(Color.White, r * 0.35f, head)
    }
    pin.label?.let {
      Text(
        it,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier =
          Modifier.anchorAt(24.dp, (-60).dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp),
      )
    }
  }
}

/** 차지하는 크기는 0, 그림은 자기 자리에서 ([x], [y]) 만큼 떨어진 곳에. */
private fun Modifier.anchorAt(x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp) =
  layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { placeable.place(x.roundToPx(), y.roundToPx()) }
  }

/** MapView 를 화면 수명에 맞춰 돌린다. */
@Composable
private fun rememberMapView(): MapView {
  val context = LocalContext.current
  val mapView = remember {
    MapLibre.getInstance(context)
    val options =
      MapLibreMapOptions.createFromAttributes(context)
        .textureMode(true) // Compose 위·아래 겹침이 자연스럽게
        .logoEnabled(false)
        .attributionEnabled(false)
        .localIdeographFontFamily("sans-serif") // 한글 지명은 차량 기본 글꼴로 그린다(글리프 파일 없이)
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

/** 화면 1dp(Compose) 가 [metersPerDp] m 가 되는 MapLibre 줌. MapLibre 는 512px 타일 · 안드로이드 dp 기준이다. */
private fun zoomFor(metersPerDp: Float, latitude: Double, composeDensity: Float, androidDensity: Float): Double {
  val metersPerAndroidDp = metersPerDp * androidDensity / composeDensity
  val metersPerDpAtZoom0 = 40_075_016.686 * cos(Math.toRadians(latitude)) / 512.0
  return (ln(metersPerDpAtZoom0 / metersPerAndroidDp) / ln(2.0)).coerceIn(2.0, 20.0)
}

private fun LatLng.toMap() = MapLatLng(latitude, longitude)

private fun LatLng.point() = Point.fromLngLat(longitude, latitude)

private fun pinColor(kind: PinKind): Color =
  when (kind) {
    PinKind.PICKUP,
    PinKind.PLACE -> WayColors.Blue
    PinKind.PICKUP_BLOCKED -> WayColors.Red
    PinKind.GHOST_BLOCKED -> WayColors.Red.copy(alpha = 0.35f)
    PinKind.GHOST -> Color(0xFFB4BAC4)
    PinKind.CAR -> WayColors.Dark
    PinKind.PASSENGER -> WayColors.Passenger
  }

private fun hex(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)

// ── 지도 위 레이어(정차 금지 구간 · 선 · 핀)

private fun addWayLayers(style: Style, context: Context) {
  val density = context.resources.displayMetrics.density
  PinKind.entries.forEach { kind -> style.addImage(iconName(kind), iconBitmap(kind, density)) }
  style.addSource(GeoJsonSource(SRC_ZONES))
  style.addSource(GeoJsonSource(SRC_LINES))
  style.addSource(GeoJsonSource(SRC_PINS))
  // 점 하나인 구간(버스정류장 둘레 10m)은 원으로 칠한다
  style.addLayer(
    FillLayer("way-zone-areas", SRC_ZONES)
      .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("Polygon")))
      .withProperties(PropertyFactory.fillColor(WayColors.Red.toArgb()), PropertyFactory.fillOpacity(0.45f))
  )
  style.addLayer(
    LineLayer("way-zone-outlines", SRC_ZONES)
      .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("Polygon")))
      .withProperties(PropertyFactory.lineColor(WayColors.Red.toArgb()), PropertyFactory.lineWidth(2f), PropertyFactory.lineOpacity(0.9f))
  )
  // 선 구간: 판정 띠(선 둘레 구간마다의 반폭 radius — StopRestrictionRules 와 같다)를 실제 축척으로 옅게 칠하고 그 위에 빨간 선.
  // 둥근 끝이라 양 끝도 판정처럼 반폭만큼 더 나간다. 화면에서 띠에 닿지 않은 핀은 정차 금지가 아니다.
  style.addLayer(
    LineLayer("way-zone-bands", SRC_ZONES)
      .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("LineString")))
      .withProperties(
        PropertyFactory.lineColor(WayColors.Red.toArgb()),
        PropertyFactory.lineOpacity(0.18f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineWidth(
          Expression.interpolate(
            Expression.exponential(2f),
            Expression.zoom(),
            Expression.stop(10, Expression.product(Expression.get("radius"), Expression.literal(metersToDp(2.0, 10.0)))),
            Expression.stop(22, Expression.product(Expression.get("radius"), Expression.literal(metersToDp(2.0, 22.0)))),
          )
        ),
      )
  )
  style.addLayer(
    LineLayer("way-zones", SRC_ZONES)
      .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("LineString")))
      .withProperties(
        PropertyFactory.lineColor(WayColors.Red.toArgb()),
        PropertyFactory.lineWidth(7f),
        PropertyFactory.lineOpacity(0.85f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
      )
  )
  val color = Expression.toColor(Expression.get("color"))
  val width = Expression.get("width")
  style.addLayer(
    LineLayer("way-lines", SRC_LINES)
      .withFilter(Expression.eq(Expression.get("dashed"), Expression.literal(false)))
      .withProperties(
        PropertyFactory.lineColor(color),
        PropertyFactory.lineWidth(width),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
      )
  )
  style.addLayer(
    LineLayer("way-lines-dashed", SRC_LINES)
      .withFilter(Expression.eq(Expression.get("dashed"), Expression.literal(true)))
      .withProperties(
        PropertyFactory.lineColor(color),
        PropertyFactory.lineWidth(width),
        PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
      )
  )
  // 핀(뾰족한 끝이 좌표) · 점(가운데가 좌표)
  val isDot = Expression.any(Expression.eq(Expression.get("kind"), PinKind.CAR.name), Expression.eq(Expression.get("kind"), PinKind.PASSENGER.name))
  fun labelProps(offset: Array<Float>) =
    arrayOf(
      PropertyFactory.iconImage(Expression.get("icon")),
      PropertyFactory.iconAllowOverlap(true),
      PropertyFactory.iconIgnorePlacement(true),
      PropertyFactory.textField(Expression.get("label")),
      PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
      PropertyFactory.textSize(14f),
      PropertyFactory.textColor(color),
      PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
      PropertyFactory.textHaloWidth(2.5f),
      PropertyFactory.textAnchor(Property.TEXT_ANCHOR_LEFT),
      PropertyFactory.textOffset(offset),
      PropertyFactory.textAllowOverlap(true),
      PropertyFactory.textIgnorePlacement(true),
    )
  style.addLayer(
    SymbolLayer("way-pins", SRC_PINS)
      .withFilter(Expression.not(isDot))
      .withProperties(PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM), *labelProps(arrayOf(1.4f, -2.2f)))
  )
  style.addLayer(
    SymbolLayer("way-dots", SRC_PINS)
      .withFilter(isDot)
      .withProperties(PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER), *labelProps(arrayOf(1.3f, 0f)))
  )
}

private fun iconName(kind: PinKind) = "way-${kind.name.lowercase()}"

private fun iconBitmap(kind: PinKind, density: Float): Bitmap {
  val color = pinColor(kind).toArgb()
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  return when (kind) {
    PinKind.CAR,
    PinKind.PASSENGER -> {
      val r = (if (kind == PinKind.CAR) 13f else 10f) * density
      val size = (r * 2 + 6 * density).toInt()
      Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
        val c = AndroidCanvas(bmp)
        val mid = size / 2f
        paint.color = android.graphics.Color.WHITE
        c.drawCircle(mid, mid, r + 3 * density, paint)
        paint.color = color
        c.drawCircle(mid, mid, r, paint)
        if (kind == PinKind.CAR) {
          val s = r * 0.55f
          paint.color = android.graphics.Color.WHITE
          c.drawPath(
            AndroidPath().apply {
              moveTo(mid, mid - s)
              lineTo(mid + s * 0.8f, mid + s)
              lineTo(mid, mid + s * 0.5f)
              lineTo(mid - s * 0.8f, mid + s)
              close()
            },
            paint,
          )
        }
      }
    }
    else -> {
      val r = 16f * density
      val w = (r * 2 + 4 * density).toInt()
      val h = (r * 3f).toInt()
      Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
        val c = AndroidCanvas(bmp)
        val cx = w / 2f
        val cy = r + 2 * density
        paint.color = color
        c.drawPath(
          AndroidPath().apply {
            moveTo(cx, h.toFloat())
            lineTo(cx - r * 0.75f, cy + r * 0.6f)
            lineTo(cx + r * 0.75f, cy + r * 0.6f)
            close()
          },
          paint,
        )
        c.drawCircle(cx, cy, r, paint)
        paint.color = android.graphics.Color.WHITE
        paint.alpha = if (kind == PinKind.GHOST || kind == PinKind.GHOST_BLOCKED) 160 else 255
        c.drawCircle(cx, cy, r * 0.35f, paint)
      }
    }
  }
}

/** 길가 구간은 선, 점 구간(정류장 등)은 반지름만큼의 원(지도 축척과 같이 커진다). */
/** 줌 [zoom] 에서 [meters] 가 지도 위 몇 dp 인지(MapLibre 타일 512 · 서울 위도). */
private fun metersToDp(meters: Double, zoom: Double): Float =
  (meters / (40_075_016.686 * cos(Math.toRadians(37.55)) / (512.0 * Math.pow(2.0, zoom)))).toFloat()

private fun zonesGeoJson(zones: List<RestrictedZone>): FeatureCollection =
  FeatureCollection.fromFeatures(
    // 지정 금지구역은 요일·시간 창마다 같은 선이 여러 개로 온다 — 한 번만 그린다
    zones.distinctBy { it.path to it.radiusMeters }.mapNotNull { z ->
      when {
        z.path.size >= 2 -> Feature.fromGeometry(LineString.fromLngLats(z.path.map { p -> p.point() })).apply { addNumberProperty("radius", z.radiusMeters) }
        z.path.size == 1 -> Feature.fromGeometry(Polygon.fromLngLats(listOf(Geo.circle(z.path.single(), z.radiusMeters).map { p -> p.point() })))
        else -> null
      }
    }
  )

private fun linesGeoJson(lines: List<MapLine>): FeatureCollection =
  FeatureCollection.fromFeatures(
    lines.filter { it.path.size >= 2 }.map { line ->
      Feature.fromGeometry(LineString.fromLngLats(line.path.map { it.point() })).apply {
        addStringProperty("color", hex(line.color))
        addNumberProperty("width", line.widthDp)
        addBooleanProperty("dashed", line.dashed)
      }
    }
  )

private fun pinsGeoJson(pins: List<MapPin>): FeatureCollection =
  FeatureCollection.fromFeatures(
    pins.map { pin ->
      Feature.fromGeometry(pin.location.point()).apply {
        addStringProperty("kind", pin.kind.name)
        addStringProperty("icon", iconName(pin.kind))
        addStringProperty("label", pin.label ?: "")
        addStringProperty("color", hex(if (pin.kind == PinKind.PASSENGER) WayColors.Orange else pinColor(pin.kind).copy(alpha = 1f)))
      }
    }
  )
