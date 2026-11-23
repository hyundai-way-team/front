package com.kscho.way.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kscho.way.presentation.component.theme.WayColors
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Polygon

internal fun pinColor(kind: PinKind): Color =
  when (kind) {
    PinKind.PICKUP,
    PinKind.PLACE -> WayColors.Blue
    PinKind.PICKUP_BLOCKED -> WayColors.Red
    PinKind.GHOST_BLOCKED -> WayColors.Red.copy(alpha = 0.35f)
    PinKind.GHOST -> Color(0xFFB4BAC4)
    PinKind.CAR -> WayColors.Dark
    PinKind.PASSENGER -> WayColors.Passenger
  }

internal fun hex(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)

internal fun addWayLayers(style: Style, context: Context) {
  val density = context.resources.displayMetrics.density
  PinKind.entries.forEach { kind -> style.addImage(iconName(kind), iconBitmap(kind, density)) }
  style.addSource(GeoJsonSource(SRC_ZONES))
  style.addSource(GeoJsonSource(SRC_LINES))
  style.addSource(GeoJsonSource(SRC_PINS))
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
  val isDot = Expression.any(Expression.eq(Expression.get("kind"), PinKind.CAR.name), Expression.eq(Expression.get("kind"), PinKind.PASSENGER.name))
  val designed = Expression.eq(Expression.get("designed"), Expression.literal(true))
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
      .withFilter(Expression.all(Expression.not(isDot), Expression.not(designed)))
      .withProperties(PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM), *labelProps(arrayOf(1.4f, -2.2f)))
  )
  style.addLayer(
    SymbolLayer("way-dots", SRC_PINS)
      .withFilter(Expression.all(isDot, Expression.not(designed)))
      .withProperties(PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER), *labelProps(arrayOf(1.3f, 0f)))
  )
  style.addLayer(
    SymbolLayer("way-markers", SRC_PINS)
      .withFilter(designed)
      .withProperties(
        PropertyFactory.iconImage(Expression.get("marker")),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
      )
  )
}

internal fun markerKey(look: WayMarkers.Look, label: String) = "way-marker-${look.name.lowercase()}-$label"

internal fun markerLabel(pin: MapPin): String = if (pin.kind == PinKind.PICKUP_BLOCKED) "픽업 위치" else pin.label ?: ""

internal fun iconName(kind: PinKind) = "way-${kind.name.lowercase()}"

internal fun iconBitmap(kind: PinKind, density: Float): Bitmap {
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
