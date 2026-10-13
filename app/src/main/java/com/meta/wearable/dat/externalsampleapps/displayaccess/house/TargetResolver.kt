package com.meta.wearable.dat.externalsampleapps.displayaccess.house

data class TextTag(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)
enum class TargetKind(val label: String) {
  SEARCHING("Searching"), READING("Reading number"), VISUAL("Visual match"), LAST_VISUAL("Last visual match"),
  ESTIMATE("Nearby estimate"), UNCERTAIN("Uncertain"), MANUAL("Manually selected"), LOCATION_WEAK("Location weak")
}
data class TargetDecision(val kind: TargetKind, val site: AddressSite? = null)
/** Address evidence, not a calibrated confidence score. All clocks are monotonic. */
class TargetResolver {
  private var pending: AddressSite? = null
  private var hits = 0
  private var firstHit = 0L
  private var lastHit = 0L
  private var locked: AddressSite? = null
  private var manual: AddressSite? = null
  private var manualUntil = 0L
  private var ambiguousUntil = 0L

  fun invalidateVisual() {
    pending = null; locked = null; hits = 0; firstHit = 0; lastHit = 0; ambiguousUntil = 0
  }

  fun reset() {
    pending = null; locked = null; manual = null; hits = 0
    firstHit = 0; lastHit = 0; manualUntil = 0; ambiguousUntil = 0
  }

  fun hold(site: AddressSite, nowMs: Long) {
    reset(); manual = site; manualUntil = nowMs + 20_000
  }

  fun observe(fix: Fix?, sites: List<AddressSite>, tags: List<TextTag>, nowMs: Long,
      candidatesComplete: Boolean = true): TargetDecision {
    if (nowMs < manualUntil) return TargetDecision(TargetKind.MANUAL, manual)
    if (!usable(fix)) return current(fix, sites, nowMs)
    val central = tags.filter {
      (it.left + it.right) / 2 in .2f.. .8f && (it.top + it.bottom) / 2 in .15f.. .85f
    }
    val numbers = central.mapNotNull { tag ->
      tag.text.uppercase().trim().takeIf { Regex("""\d{1,6}\s?[A-Z]?""").matches(it) }
          ?.replace(" ", "")
    }.toSet()
    var matched = sites.distinctBy { it.fullAddress }.filter { normalizeNumber(it.civicNumber) in numbers }
    if (matched.size > 1) {
      val streetMatches = matched.filter { site ->
        val street = normalizeStreet(site.streetName + " " + site.streetType)
        val direction = normalizeStreet(site.streetDirection)
        val names = if (direction.isEmpty()) listOf(street) else listOf("$direction $street", "$street $direction")
        tags.any { tag -> names.any { name ->
          name.length > 3 && Regex("(?:^|\\s)${Regex.escape(name)}(?:\\s|$)").containsMatchIn(normalizeStreet(tag.text))
        } }
      }
      if (streetMatches.size == 1) matched = streetMatches
    }
    if (matched.size > 1 || (matched.isNotEmpty() && !candidatesComplete)) {
      pending = null; locked = null; hits = 0; ambiguousUntil = nowMs + 1500
      return TargetDecision(TargetKind.UNCERTAIN)
    }
    val candidate = matched.singleOrNull() ?: return current(fix, sites, nowMs)
    ambiguousUntil = 0
    if (pending?.fullAddress != candidate.fullAddress || nowMs - firstHit > 2000) {
      // A new conflicting number immediately invalidates the previous visual lock.
      if (locked?.fullAddress != candidate.fullAddress) locked = null
      pending = candidate; hits = 0; firstHit = nowMs
    }
    if (hits == 0 || nowMs > lastHit) hits++
    lastHit = nowMs
    if (hits >= 3) locked = candidate
    return current(fix, sites, nowMs)
  }

  fun current(fix: Fix?, sites: List<AddressSite>, nowMs: Long,
      cameraActive: Boolean = true): TargetDecision {
    if (nowMs < manualUntil) return TargetDecision(TargetKind.MANUAL, manual)
    if (!usable(fix)) {
      locked = null; pending = null; hits = 0
      return TargetDecision(if (fix == null) TargetKind.SEARCHING else TargetKind.LOCATION_WEAK)
    }
    if (!cameraActive || nowMs - lastHit >= 5000) {
      locked = null; pending = null; hits = 0; ambiguousUntil = 0
    }
    if (cameraActive && nowMs < ambiguousUntil) return TargetDecision(TargetKind.UNCERTAIN)
    val valid = sites.map { it.fullAddress }.toSet()
    locked?.takeIf { it.fullAddress in valid }?.let {
      return TargetDecision(if (nowMs - lastHit < 2000) TargetKind.VISUAL else TargetKind.LAST_VISUAL, it)
    }
    pending?.takeIf { it.fullAddress in valid && nowMs - lastHit < 2000 }?.let {
      return TargetDecision(TargetKind.READING, it)
    }
    return HouseLocator.rank(fix!!, sites).firstOrNull()?.let {
      TargetDecision(TargetKind.ESTIMATE, it)
    } ?: TargetDecision(TargetKind.SEARCHING)
  }

  private fun usable(fix: Fix?) = fix != null && fix.ageMs <= 15_000 &&
      fix.accuracyM.isFinite() && fix.accuracyM in 0f..50f

  companion object {
    fun normalizeNumber(s: String) = s.uppercase().replace(" ", "")
    fun normalizeStreet(s: String): String = s.uppercase().replace(Regex("[^A-Z0-9 ]"), " ")
        .split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") {
          when (it) { "STREET" -> "ST"; "AVENUE" -> "AVE"; "ROAD" -> "RD"; "DRIVE" -> "DR"; "WEST" -> "W"; "EAST" -> "E"; "NORTH" -> "N"; "SOUTH" -> "S"; else -> it }
        }
  }
}
