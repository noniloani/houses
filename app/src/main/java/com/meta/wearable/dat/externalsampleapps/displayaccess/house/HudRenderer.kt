package com.meta.wearable.dat.externalsampleapps.displayaccess.house

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Draws the full-colour property HUD as a bitmap. The glasses display is additive, so black
 * reads as see-through and bright colours float over the street. Kept to 560x360 so it
 * stays well under the 600x600 panel and is quick to send over Bluetooth.
 */
object HudRenderer {
  const val WIDTH = 560
  const val HEIGHT = 360

  private val GREEN = Color.rgb(64, 255, 160)
  private val CYAN = Color.rgb(80, 210, 255)
  private val AMBER = Color.rgb(255, 196, 64)
  private val RED = Color.rgb(255, 96, 96)
  private val WHITE = Color.rgb(245, 245, 245)
  private val GREY = Color.rgb(160, 168, 180)
  private val PANEL = Color.rgb(22, 30, 44)

  fun render(p: PropertyAssessment, listing: ListingStatus, distanceM: Int?): Bitmap {
    val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.BLACK)
    val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    // Accent bar + address
    c.drawRoundRect(RectF(0f, 0f, 8f, 70f), 4f, 4f, fill(CYAN))
    val street = p.address.substringBefore(" V").ifBlank { p.address }
    c.drawText(ellipsize(titleCase(street), 30), 22f, 32f, text(WHITE, 28f, bold))
    val sub = listOfNotNull(
        distanceM?.let { "~${it} m away" },
        p.description?.let { titleCase(it) },
    ).joinToString("  ·  ")
    c.drawText(ellipsize(sub, 46), 22f, 62f, text(GREY, 19f))

    // Big assessed value
    val total = p.totalValue
    c.drawText(if (total != null) money(total) else "No value", 20f, 130f, text(GREEN, 58f, bold))
    val year = p.assessmentDate?.let { Regex("""\b(20\d\d)\b""").find(it)?.value }
    c.drawText("BC Assessment ${year ?: ""}".trim(), 22f, 158f, text(GREY, 18f))

    // Year-over-year badge
    p.yearOverYearPct?.let { pct ->
      val up = pct >= 0
      val label = String.format(Locale.CANADA, "%s %.1f%%", if (up) "▲" else "▼", abs(pct))
      val paint = text(if (up) GREEN else RED, 22f, bold)
      val w = paint.measureText(label) + 24f
      val r = RectF(WIDTH - w - 16f, 92f, WIDTH - 16f, 128f)
      c.drawRoundRect(r, 18f, 18f, fill(PANEL))
      c.drawText(label, r.left + 12f, r.bottom - 10f, paint)
      c.drawText("vs last year", r.left + 8f, 152f, text(GREY, 16f))
    }

    // Stat tiles
    val tiles = listOf(
        "LAND" to (p.landSize ?: "—"),
        "BUILT" to (p.yearBuilt ?: "—"),
        "BED / BATH" to "${p.bedrooms ?: "—"} / ${p.baths ?: "—"}",
        "FLOOR" to (p.floorAreaSqFt?.let { "${NumberFormat.getIntegerInstance(Locale.CANADA).format(it)} ft²" } ?: "—"),
    )
    val tileW = (WIDTH - 20f * 2 - 10f * 3) / 4
    tiles.forEachIndexed { i, (k, v) ->
      val left = 20f + i * (tileW + 10f)
      c.drawRoundRect(RectF(left, 176f, left + tileW, 240f), 12f, 12f, fill(PANEL))
      c.drawText(k, left + 10f, 198f, text(CYAN, 14f, bold))
      c.drawText(ellipsize(v, 12), left + 10f, 228f, text(WHITE, 19f, bold))
    }

    // Land vs building split
    val land = p.landValue
    val bldg = p.buildingValue
    if (land != null && bldg != null && land + bldg > 0) {
      val frac = land.toFloat() / (land + bldg)
      val bar = RectF(20f, 256f, WIDTH - 20f, 268f)
      c.drawRoundRect(bar, 6f, 6f, fill(AMBER))
      c.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * frac, bar.bottom), 6f, 6f, fill(GREEN))
      c.drawText("Land ${short(land)}", 20f, 290f, text(GREEN, 17f))
      val b = "Building ${short(bldg)}"
      val bp = text(AMBER, 17f)
      c.drawText(b, WIDTH - 20f - bp.measureText(b), 290f, bp)
    }

    // 10-year sparkline
    sparkline(c, p.tenYearValues, RectF(20f, 320f, 300f, 352f))
    if (p.tenYearValues.size > 1) c.drawText("10-yr trend", 20f, 312f, text(GREY, 13f))

    // Listing status
    val (label, color) = when (listing) {
      is ListingStatus.ForSale -> "FOR SALE ${listing.askingPrice?.let { money(it) } ?: ""}".trim() to GREEN
      ListingStatus.NotListed -> "Not listed" to GREY
      ListingStatus.Unknown -> "Sale status: no feed" to GREY
    }
    val lp = text(color, 18f, bold)
    val recent = if (p.hasRecentSale) "Sold recently" else null
    c.drawText(label, WIDTH - 20f - lp.measureText(label), 330f, lp)
    recent?.let {
      val rp = text(AMBER, 16f, bold)
      c.drawText(it, WIDTH - 20f - rp.measureText(it), 350f, rp)
    }
    return bmp
  }

  private fun sparkline(c: Canvas, values: List<Long>, box: RectF) {
    if (values.size < 2) return
    val min = values.min().toFloat()
    val max = values.max().toFloat().coerceAtLeast(min + 1)
    val step = box.width() / (values.size - 1)
    val path = Path()
    values.forEachIndexed { i, v ->
      val x = box.left + i * step
      val y = box.bottom - (v - min) / (max - min) * box.height()
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = CYAN; style = Paint.Style.STROKE; strokeWidth = 3f
    })
    val lastY = box.bottom - (values.last() - min) / (max - min) * box.height()
    c.drawCircle(box.right, lastY, 5f, fill(GREEN))
  }

  private fun text(color: Int, size: Float, face: Typeface = Typeface.DEFAULT) =
      Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = face }

  private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

  fun money(v: Long): String = "$" + NumberFormat.getIntegerInstance(Locale.CANADA).format(v)

  fun short(v: Long): String = when {
    v >= 1_000_000 -> String.format(Locale.CANADA, "$%.2fM", v / 1_000_000.0)
    v >= 1_000 -> "$${v / 1_000}K"
    else -> "$$v"
  }

  private fun ellipsize(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1) + "…"

  private fun titleCase(s: String) =
      s.lowercase().split(' ').joinToString(" ") { w ->
        if (w.length <= 1 || w.any(Char::isDigit) && w.length <= 4) w.uppercase()
        else w.replaceFirstChar(Char::uppercase)
      }
}
