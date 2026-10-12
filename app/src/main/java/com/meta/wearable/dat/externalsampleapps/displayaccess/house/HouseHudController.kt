package com.meta.wearable.dat.externalsampleapps.displayaccess.house

import android.content.Context
import android.util.Log
import com.meta.wearable.dat.display.views.Alignment
import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.ContentScope
import com.meta.wearable.dat.display.views.CornerRadius
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.FlexBoxScope
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.ImageSize
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the BC house HUD: finds the address the wearer is facing, pulls its BC Assessment record
 * and pushes a colour HUD to the glasses. Follows the wearer automatically; Prev/Next on the
 * glasses cycle through nearby houses, and Rescan forces a fresh pick.
 */
class HouseHudController(
    context: Context,
    private val scope: CoroutineScope,
    private val send: (ContentScope.() -> Unit) -> Unit,
    private val listings: ListingProvider = ListingProvider.NONE,
) {
  private companion object {
    const val TAG = "HouseHud"
    const val SEARCH_RADIUS_M = 70
    const val MOVE_RESCAN_M = 12.0
    const val TURN_RESCAN_DEG = 25f
    const val MANUAL_HOLD_MS = 20_000L
  }

  private val locator = HouseLocator(context.applicationContext)
  private val geocoder = BcGeocoderClient()
  private val assessments = BcAssessmentClient()
  private val cache = object : LinkedHashMap<String, PropertyAssessment?>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PropertyAssessment?>?) =
        size > 60
  }

  private var followJob: Job? = null
  private var lookupJob: Job? = null
  private var candidates: List<AddressSite> = emptyList()
  private var index = 0
  private var lastPickFix: Fix? = null
  private var manualUntil = 0L

  fun start() {
    stop()
    locator.start()
    showMessage("Scanning", "Finding the house you're facing…", IconName.HOUSE)
    followJob = scope.launch {
      while (isActive) {
        val fix = locator.fix
        if (fix == null) {
          if (candidates.isEmpty()) showMessage("Waiting for GPS", "Step outside or turn on location.", IconName.COMPASS_NORTH_UP_RED)
        } else if (shouldRescan(fix)) {
          rescan(fix)
        }
        delay(3_000)
      }
    }
  }

  fun stop() {
    followJob?.cancel()
    lookupJob?.cancel()
    followJob = null
    lookupJob = null
    locator.stop()
    candidates = emptyList()
    lastPickFix = null
  }

  private fun shouldRescan(fix: Fix): Boolean {
    if (System.currentTimeMillis() < manualUntil) return false
    val last = lastPickFix ?: return true
    val moved = HouseLocator.distanceM(last.latitude, last.longitude, fix.latitude, fix.longitude)
    val turned = if (last.headingDeg != null && fix.headingDeg != null) {
      var d = abs(last.headingDeg - fix.headingDeg)
      if (d > 180) d = 360 - d
      d
    } else 0f
    return moved > MOVE_RESCAN_M || turned > TURN_RESCAN_DEG
  }

  private suspend fun rescan(fix: Fix) {
    lastPickFix = fix
    val sites = withContext(Dispatchers.IO) {
      runCatching { geocoder.sitesNear(fix.latitude, fix.longitude, SEARCH_RADIUS_M, 12) }
          .onFailure { Log.w(TAG, "Geocoder failed", it) }
          .getOrDefault(emptyList())
    }
    if (sites.isEmpty()) {
      showMessage("No address here", "No BC civic address within $SEARCH_RADIUS_M m.", IconName.HOUSE)
      return
    }
    val ranked = HouseLocator.rank(fix, sites)
    if (ranked.firstOrNull() == candidates.getOrNull(index)) return // Same house, keep the HUD.
    candidates = ranked
    index = 0
    showCurrent()
  }

  private fun rescanNow() {
    manualUntil = 0
    lastPickFix = null
    showMessage("Rescanning", "Finding the house you're facing…", IconName.TWO_ARROWS_CLOCKWISE)
    locator.fix?.let { fix -> scope.launch { rescan(fix) } }
  }

  private fun step(delta: Int) {
    if (candidates.isEmpty()) return
    manualUntil = System.currentTimeMillis() + MANUAL_HOLD_MS
    index = (index + delta + candidates.size) % candidates.size
    showCurrent()
  }

  private fun showCurrent() {
    val site = candidates.getOrNull(index) ?: return
    lookupJob?.cancel()
    showMessage(site.fullAddress.substringBefore(","), "Pulling BC Assessment…", IconName.HOUSE)
    lookupJob = scope.launch {
      val result = withContext(Dispatchers.IO) { lookup(site) }
      val fix = locator.fix
      val dist = fix?.let {
        HouseLocator.distanceM(it.latitude, it.longitude, site.latitude, site.longitude).toInt()
      }
      when (result) {
        is Lookup.Found -> {
          val listing = withContext(Dispatchers.IO) {
            runCatching { listings.statusFor(site, result.assessment) }.getOrDefault(ListingStatus.Unknown)
          }
          val bitmap = HudRenderer.render(result.assessment, listing, dist)
          send {
            flexBox(direction = Direction.COLUMN, gap = 8) {
              image(bitmap = bitmap, sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
              controls()
            }
          }
        }
        Lookup.MultiUnit ->
            showMessage(site.fullAddress.substringBefore(","), "Multi-unit building. Each unit has its own assessment.", IconName.THREE_PEOPLE_OVERLAPPING)
        Lookup.NotFound ->
            showMessage(site.fullAddress.substringBefore(","), "No BC Assessment record found for this address.", IconName.HOUSE)
        Lookup.Failed ->
            showMessage(site.fullAddress.substringBefore(","), "BC Assessment didn't answer. Tap Rescan to retry.", IconName.TWO_ARROWS_CLOCKWISE)
      }
    }
  }

  private sealed interface Lookup {
    data class Found(val assessment: PropertyAssessment) : Lookup
    data object MultiUnit : Lookup
    data object NotFound : Lookup
    data object Failed : Lookup
  }

  private fun lookup(site: AddressSite): Lookup {
    val key = site.fullAddress
    if (cache.containsKey(key)) return cache[key]?.let { Lookup.Found(it) } ?: Lookup.NotFound
    return try {
      val matches = assessments.search(site)
      val match = matches.firstOrNull() ?: return Lookup.NotFound.also { cache[key] = null }
      if (match.isMultiUnit) return Lookup.MultiUnit
      val info = assessments.propertyInfo(match.oid)
      cache[key] = info
      info?.let { Lookup.Found(it) } ?: Lookup.NotFound
    } catch (t: Throwable) {
      Log.w(TAG, "BC Assessment lookup failed", t)
      Lookup.Failed
    }
  }

  private fun showMessage(title: String, body: String, icon: IconName) {
    send {
      flexBox(direction = Direction.COLUMN, gap = 8) {
        flexBox(padding = 24, gap = 8, background = FlexBoxBackground.CARD) {
          flexBox(direction = Direction.ROW, gap = 12, crossAlignment = Alignment.CENTER) {
            icon(name = icon)
            text(title, style = TextStyle.HEADING)
          }
          text(body, style = TextStyle.BODY, color = TextColor.SECONDARY)
        }
        controls()
      }
    }
  }

  private fun FlexBoxScope.controls() {
    flexBox(direction = Direction.ROW, gap = 8, alignment = Alignment.CENTER, crossAlignment = Alignment.CENTER) {
      buttonGroup {
        button("Prev", style = ButtonStyle.SECONDARY, iconName = IconName.ARROW_LEFT, onClick = { scope.launch { step(-1) } })
        button("Rescan", style = ButtonStyle.PRIMARY, iconName = IconName.TWO_ARROWS_CLOCKWISE, onClick = { scope.launch { rescanNow() } })
        button("Next", style = ButtonStyle.SECONDARY, iconName = IconName.ARROW_RIGHT, onClick = { scope.launch { step(1) } })
      }
    }
  }
}
