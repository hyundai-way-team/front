package com.kscho.way.presentation.map

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.kscho.way.domain.model.geo.Geo
import com.kscho.way.domain.model.restriction.RestrictedZone
import com.kscho.way.presentation.component.theme.WayColors
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Polygon

internal fun zonesGeoJson(zones: List<RestrictedZone>): FeatureCollection =
  FeatureCollection.fromFeatures(
    zones.distinctBy { it.path to it.radiusMeters }.mapNotNull { z ->
      when {
        z.path.size >= 2 -> Feature.fromGeometry(LineString.fromLngLats(z.path.map { p -> p.point() })).apply { addNumberProperty("radius", z.radiusMeters) }
        z.path.size == 1 -> Feature.fromGeometry(Polygon.fromLngLats(listOf(Geo.circle(z.path.single(), z.radiusMeters).map { p -> p.point() })))
        else -> null
      }
    }
  )

internal fun linesGeoJson(lines: List<MapLine>): FeatureCollection =
  FeatureCollection.fromFeatures(
    lines.filter { it.path.size >= 2 }.map { line ->
      Feature.fromGeometry(LineString.fromLngLats(line.path.map { it.point() })).apply {
        addStringProperty("color", hex(line.color))
        addNumberProperty("width", line.widthDp)
        addBooleanProperty("dashed", line.dashed)
      }
    }
  )

internal fun pinsGeoJson(pins: List<MapPin>): FeatureCollection =
  FeatureCollection.fromFeatures(
    pins.map { pin ->
      Feature.fromGeometry(pin.location.point()).apply {
        addStringProperty("kind", pin.kind.name)
        addStringProperty("icon", iconName(pin.kind))
        addStringProperty("label", pin.label ?: "")
        addStringProperty("color", hex(if (pin.kind == PinKind.PASSENGER) WayColors.Orange else pinColor(pin.kind).copy(alpha = 1f)))
        val look = WayMarkers.lookOf(pin.kind)
        addBooleanProperty("designed", look != null)
        if (look != null) addStringProperty("marker", markerKey(look, markerLabel(pin)))
      }
    }
  )
