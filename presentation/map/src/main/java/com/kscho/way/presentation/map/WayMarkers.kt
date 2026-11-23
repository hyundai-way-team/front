package com.kscho.way.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.kscho.way.presentation.component.R
import kotlin.math.ceil

internal object WayMarkers {
  enum class Look(val shape: MarkerGeometry.Shape, val tagFill: Int, val tagBorder: Int, val tagText: Int, val tagShadow: Boolean) {
    PICKUP(MarkerGeometry.Shape.PIN, PRIMARY_500, PRIMARY_500, WHITE, false),
    PLACE(MarkerGeometry.Shape.PIN, PRIMARY_50, PRIMARY_500, PRIMARY_500, true),
    PASSENGER(MarkerGeometry.Shape.DOT, WARNING_50, WARNING_600, WARNING_600, true),
  }

  fun lookOf(kind: PinKind): Look? =
    when (kind) {
      PinKind.PICKUP,
      PinKind.PICKUP_BLOCKED -> Look.PICKUP
      PinKind.PLACE -> Look.PLACE
      PinKind.PASSENGER -> Look.PASSENGER
      else -> null
    }

  class Drawn(val bitmap: Bitmap, val geometry: MarkerGeometry)

  fun draw(context: Context, look: Look, label: String, scale: Float): Drawn {
    val text =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.pretendard_medium)
        textSize = TAG_TEXT_SIZE
        letterSpacing = TAG_LETTER_SPACING
        textAlign = Paint.Align.CENTER
        color = look.tagText
      }
    val tagWidth = text.measureText(label) + 2 * (TAG_PADDING_H + TAG_BORDER)
    val g = MarkerGeometry.of(look.shape, tagWidth)
    val bitmap = Bitmap.createBitmap(ceil(g.width * scale).toInt(), ceil(g.height * scale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)

    val tag = RectF(g.tagLeft, g.tagTop, g.tagLeft + tagWidth, g.tagTop + MarkerGeometry.TAG_HEIGHT)
    val fill =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = look.tagFill
        if (look.tagShadow) setShadowLayer(TAG_SHADOW_RADIUS * scale, 0f, 0f, TAG_SHADOW)
      }
    canvas.drawRoundRect(tag, TAG_RADIUS, TAG_RADIUS, fill)
    val half = TAG_BORDER / 2
    canvas.drawRoundRect(
      RectF(tag.left + half, tag.top + half, tag.right - half, tag.bottom - half),
      TAG_RADIUS - half,
      TAG_RADIUS - half,
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = TAG_BORDER
        color = look.tagBorder
      },
    )
    val fm = text.fontMetrics
    canvas.drawText(label, tag.centerX(), tag.centerY() - (fm.ascent + fm.descent) / 2, text)

    canvas.save()
    canvas.translate(g.graphicLeft, g.graphicTop)
    when (look.shape) {
      MarkerGeometry.Shape.PIN -> {
        canvas.drawPath(
          PathParser.createPathFromPathData(PIN_BODY),
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY_500
            setShadowLayer(PIN_GLOW_RADIUS * scale, 0f, 0f, PIN_GLOW)
          },
        )
        canvas.drawPath(PathParser.createPathFromPathData(PIN_HOLE), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
      }
      MarkerGeometry.Shape.DOT -> {
        canvas.drawCircle(
          28f,
          28f,
          12f,
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            setShadowLayer(DOT_SHADOW_RADIUS * scale, 0f, 0f, DOT_SHADOW)
          },
        )
        canvas.drawCircle(28f, 28f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DOT_PASSENGER })
      }
    }
    canvas.restore()
    return Drawn(bitmap, g)
  }

  private const val TAG_TEXT_SIZE = 16f
  private const val TAG_LETTER_SPACING = -0.02f
  private const val TAG_PADDING_H = 8f
  private const val TAG_BORDER = 1f
  private const val TAG_RADIUS = 12f
  private const val TAG_SHADOW_RADIUS = 4.3f
  private const val TAG_SHADOW = 0x330F172A
  private const val PIN_GLOW_RADIUS = 8f
  private const val PIN_GLOW = 0xFF93ACFD.toInt()
  private const val DOT_SHADOW_RADIUS = 13f
  private const val DOT_SHADOW = 0x990F172A.toInt()

  private const val PIN_BODY =
    "M26.5 10C35.6125 10 43 17.3875 43 26.5V27.4023C43.0004 31.0294 41.8679 34.5671 39.7598 37.5186L27.751 54.3291C27.6087 54.5279 27.4206 54.6919 27.2031 54.8037C26.9857 54.9154 26.7444 54.9736 26.5 54.9736C26.2556 54.9736 26.0143 54.9154 25.7969 54.8037C25.5794 54.6919 25.3913 54.5279 25.249 54.3291L13.2402 37.5186C11.1324 34.5667 10 31.0297 10 27.4023V26.5C10 17.3875 17.3875 10 26.5 10Z"
  private const val PIN_HOLE =
    "M23.5678 20.5013C24.3683 20.1069 25.2391 19.875 26.1297 19.819C27.0204 19.763 27.9133 19.8839 28.757 20.1749C29.6007 20.4658 30.3783 20.921 31.0451 21.5141C31.7119 22.1072 32.2547 22.8265 32.6419 23.6305C33.0292 24.4346 33.2534 25.3073 33.3016 26.1984C33.3498 27.0895 33.221 27.9814 32.9226 28.8224C32.6242 29.6635 32.1622 30.4371 31.5632 31.0987C30.9643 31.7602 30.2402 32.2966 29.4328 32.6768C27.8181 33.4545 25.9606 33.559 24.2689 32.9672C22.5772 32.3754 21.1899 31.1358 20.4122 29.5211C19.6344 27.9065 19.5299 26.049 20.1217 24.3573C20.7135 22.6656 21.9531 21.2783 23.5678 20.5005"
}

private const val WHITE = 0xFFFFFFFF.toInt()
private const val PRIMARY_500 = 0xFF3B67F6.toInt()
private const val PRIMARY_50 = 0xFFF5F7FF.toInt()
private const val WARNING_50 = 0xFFFFFBEB.toInt()
private const val WARNING_600 = 0xFFD97706.toInt()
private const val DOT_PASSENGER = 0xFFF59E0B.toInt()
