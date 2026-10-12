package com.meta.wearable.dat.externalsampleapps.displayaccess.house

/** A BC Assessment record, parsed from the public property information page. */
data class PropertyAssessment(
    val address: String,
    val assessmentDate: String?,
    val totalValue: Long?,
    val landValue: Long?,
    val buildingValue: Long?,
    val previousTotalValue: Long?,
    val yearBuilt: String?,
    val description: String?,
    val bedrooms: String?,
    val baths: String?,
    val landSize: String?,
    val floorAreaSqFt: Int?,
    val storeys: String?,
    val salesHistory: String?,
    /** Ten-year total value history, oldest first. */
    val tenYearValues: List<Long>,
) {
  /** Percent change against last year's assessment, or null when unknown. */
  val yearOverYearPct: Double?
    get() {
      val now = totalValue ?: return null
      val before = previousTotalValue?.takeIf { it > 0 } ?: return null
      return (now - before) * 100.0 / before
    }

  /** True when the sales history section lists at least one recent sale. */
  val hasRecentSale: Boolean
    get() = salesHistory != null && !salesHistory.startsWith("No sales", ignoreCase = true)
}

/**
 * Parses the HTML fragment returned by BC Assessment's `POST /Property/PropertyInformation`.
 * Pure Kotlin (no Android types) so it runs in JVM unit tests.
 */
object BcAssessmentParser {

  fun parse(html: String): PropertyAssessment? {
    val address = byId(html, "mainaddresstitle") ?: return null
    val total = money(byId(html, "lblTotalAssessedValue"))
    if (total == null && byId(html, "lblLandSize") == null) return null
    val floor =
        listOf("lblFirstFloorArea", "lblSecondFloorArea", "lblBasementFinishArea")
            .mapNotNull { number(byId(html, it)) }
            .sum()
            .takeIf { it > 0 }
    return PropertyAssessment(
        address = address,
        assessmentDate = byId(html, "lblLastAssessmentDate"),
        totalValue = total,
        landValue = money(byId(html, "lblTotalAssessedLand")),
        buildingValue = money(byId(html, "lblTotalAssessedBuilding")),
        previousTotalValue = money(byId(html, "lblPreviousAssessedValue")),
        yearBuilt = byId(html, "lblYearBuilt"),
        description = byId(html, "lblDescription"),
        bedrooms = byId(html, "lblBedrooms"),
        baths = byId(html, "lblBathRooms"),
        landSize = byId(html, "lblLandSize"),
        floorAreaSqFt = floor,
        storeys = byId(html, "lblStoriesBuilding"),
        salesHistory = salesHistory(html),
        tenYearValues = jsArray(html, "y10value"),
    )
  }

  /** Text content of the element with this id, with tags stripped and whitespace collapsed. */
  internal fun byId(html: String, id: String): String? {
    val start = Regex("""id="${Regex.escape(id)}"[^>]*>""").find(html) ?: return null
    val tag = Regex("""<(\w+)""").findAll(html.substring(0, start.range.first)).lastOrNull()
        ?.groupValues?.get(1) ?: "span"
    val rest = html.substring(start.range.last + 1)
    val end = rest.indexOf("</$tag>").takeIf { it >= 0 } ?: rest.length
    return clean(rest.substring(0, end))
  }

  private fun salesHistory(html: String): String? {
    val start = html.indexOf("id=\"lblSalesHistory\"").takeIf { it >= 0 } ?: return null
    val open = html.indexOf('>', start).takeIf { it >= 0 } ?: return null
    val end = html.indexOf("legal-separator", open).let { if (it < 0) html.length else it }
    return clean(html.substring(open + 1, end).substringBeforeLast("<hr"))?.take(160)
  }

  internal fun jsArray(html: String, name: String): List<Long> {
    val m = Regex("""${Regex.escape(name)}\s*=\s*\[([^\]]*)]""").find(html) ?: return emptyList()
    return m.groupValues[1].split(',').mapNotNull { it.trim().toDoubleOrNull()?.toLong() }
  }

  internal fun money(text: String?): Long? =
      text?.let { Regex("""\$\s*([0-9,]+)""").find(it)?.groupValues?.get(1) }
          ?.replace(",", "")
          ?.toLongOrNull()

  private fun number(text: String?): Int? =
      text?.let { Regex("""[0-9][0-9,]*""").find(it)?.value }?.replace(",", "")?.toIntOrNull()

  private fun clean(fragment: String): String? =
      fragment
          .replace(Regex("<[^>]+>"), " ")
          .replace("&amp;", "&")
          .replace("&nbsp;", " ")
          .replace("&#39;", "'")
          .replace(Regex("""\s+"""), " ")
          .trim()
          .takeIf { it.isNotEmpty() }
}
