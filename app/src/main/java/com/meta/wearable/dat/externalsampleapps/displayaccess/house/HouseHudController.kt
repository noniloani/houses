package com.meta.wearable.dat.externalsampleapps.displayaccess.house

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.meta.wearable.dat.display.views.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HouseScanState(
    val active: Boolean = false,
    val target: TargetDecision = TargetDecision(TargetKind.SEARCHING),
    val assessment: PropertyAssessment? = null,
    val candidates: List<AddressSite> = emptyList(),
    val locationAccuracyM: Float? = null,
    val locationAgeMs: Long? = null,
    val loading: Boolean = false,
    val message: String = "Start scanning to find a house",
)

/** Fuses live visual evidence with nearby civic addresses. All mutations run on Main. */
class HouseHudController(
    context: Context,
    private val scope: CoroutineScope,
    private val send: (ContentScope.() -> Unit) -> Unit,
    private val listings: ListingProvider = ListingProvider.NONE,
) {
  private val appContext = context.applicationContext
  private val locator = HouseLocator(appContext)
  private val geocoder = BcGeocoderClient()
  private val assessments = BcAssessmentClient()
  private val resolver = TargetResolver()
  private val _state = MutableStateFlow(HouseScanState())
  val state = _state.asStateFlow()
  private val cache = object : LinkedHashMap<String, PropertyAssessment>(64, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PropertyAssessment>?) = size > 60
  }
  private val networkMutex = Mutex()
  private var followJob: Job? = null
  private var candidateJob: Job? = null
  private var lookupJob: Job? = null
  private var candidateFix: Fix? = null
  private var candidateFetchedAt = 0L
  private var lastRefreshAt = 0L
  private var generation = 0L
  private var candidateGeneration = 0L
  private var cameraActive = false
  private var lastHudKey = ""
  private var selectedListing: ListingStatus = ListingStatus.Unknown

  fun start() {
    stop()
    if (!hasLocationPermission()) {
      _state.value = HouseScanState(message = "Location permission required. Open App permissions and allow precise location.")
      send { flexBox(padding = 20, direction = Direction.COLUMN, gap = 8) {
        text("Location permission required", style = TextStyle.HEADING)
        text("Allow precise location in Android app permissions, then start again.", style = TextStyle.BODY)
      } }
      return
    }
    _state.value = HouseScanState(active = true, message = "Waiting for location…")
    locator.start()
    followJob = scope.launch {
      while (isActive) {
        if (!hasLocationPermission()) {
          stop()
          _state.value = HouseScanState(message = "Location permission revoked. Open App permissions and allow precise location.")
          return@launch
        }
        val fix = locator.fix
        val now = SystemClock.elapsedRealtime()
        _state.update { it.copy(locationAccuracyM = fix?.accuracyM, locationAgeMs = fix?.ageMs) }
        val moved = candidateFix?.let {
          fix != null && HouseLocator.distanceM(it.latitude, it.longitude, fix.latitude, fix.longitude) > 8
        } ?: true
        if (moved || now - candidateFetchedAt > 30_000) {
          if (_state.value.candidates.isNotEmpty()) {
            _state.update { it.copy(candidates = emptyList()) }
            resolver.invalidateVisual()
          }
        }
        if (fix != null && fix.ageMs <= 15_000 && fix.accuracyM <= 50 &&
            candidateJob?.isActive != true && (lastRefreshAt == 0L ||
                now - lastRefreshAt > if (moved) 3000 else 15_000)) refresh(fix)
        applyDecision(resolver.current(fix, _state.value.candidates, now, cameraActive))
        delay(500)
      }
    }
  }

  private fun hasLocationPermission() = androidx.core.content.ContextCompat.checkSelfPermission(appContext,
      android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

  fun stop() {
    generation++; candidateGeneration++
    followJob?.cancel(); candidateJob?.cancel(); lookupJob?.cancel()
    followJob = null; candidateJob = null; lookupJob = null
    locator.stop(); resolver.reset()
    candidateFix = null; candidateFetchedAt = 0; lastRefreshAt = 0
    cameraActive = false; lastHudKey = ""
    _state.value = HouseScanState()
  }

  fun cameraAvailability(available: Boolean) {
    cameraActive = available
    if (_state.value.active) applyDecision(resolver.current(locator.fix, _state.value.candidates,
        SystemClock.elapsedRealtime(), available))
  }

  fun observe(tags: List<TextTag>, capturedAtMs: Long) {
    if (!_state.value.active || !cameraActive || SystemClock.elapsedRealtime() - capturedAtMs >= 1500) return
    applyDecision(resolver.observe(locator.fix, _state.value.candidates, tags, capturedAtMs,
        candidatesComplete = _state.value.candidates.size < 50))
  }

  fun rescanNow() {
    if (!_state.value.active) return
    resolver.reset(); generation++
    lookupJob?.cancel(); candidateJob?.cancel(); candidateGeneration++
    _state.update { it.copy(target = TargetDecision(TargetKind.SEARCHING), assessment = null,
        loading = false, message = "Rescanning nearby addresses…", candidates = emptyList()) }
    candidateFix = null; lastRefreshAt = 0; lastHudKey = ""
    locator.fix?.let(::refresh)
    publishHud()
  }

  fun select(site: AddressSite) {
    if (!_state.value.active || site !in _state.value.candidates) return
    resolver.hold(site, SystemClock.elapsedRealtime())
    applyDecision(resolver.current(locator.fix, _state.value.candidates, SystemClock.elapsedRealtime(), cameraActive))
  }

  fun step(delta: Int) {
    val sites = _state.value.candidates
    if (sites.isEmpty()) return
    val index = sites.indexOf(_state.value.target.site).coerceAtLeast(0)
    select(sites[(index + delta + sites.size) % sites.size])
  }

  private fun refresh(fix: Fix) {
    lastRefreshAt = SystemClock.elapsedRealtime()
    val run = ++candidateGeneration
    candidateJob = scope.launch {
      try {
        val radius = (70 + fix.accuracyM.toInt()).coerceIn(70, 150)
        val sites = withContext(Dispatchers.IO) { geocoder.sitesNear(fix.latitude, fix.longitude, radius, 50) }
        if (run != candidateGeneration || !_state.value.active) return@launch
        candidateFix = fix; candidateFetchedAt = SystemClock.elapsedRealtime()
        _state.update { it.copy(candidates = HouseLocator.rank(fix, sites),
            message = if (sites.isEmpty()) "No nearby BC address. Move closer and rescan." else it.message) }
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (error: Exception) {
        if (run == candidateGeneration) _state.update { it.copy(message = "Address service unavailable. Tap Rescan to retry.") }
      }
    }
  }

  private fun applyDecision(decision: TargetDecision) {
    val old = _state.value.target
    val changed = old.site?.fullAddress != decision.site?.fullAddress
    _state.update { it.copy(target = decision) }
    if (changed) {
      val run = ++generation
      lookupJob?.cancel()
      _state.update { it.copy(assessment = null, loading = decision.site != null,
          message = if (decision.site != null) "Looking up BC Assessment…" else when (decision.kind) {
            TargetKind.UNCERTAIN -> "Several addresses fit. Aim at the number or select a house below."
            TargetKind.LOCATION_WEAK -> "Location is weak. Step outside and enable precise location."
            else -> "Aim toward a readable house number."
          }) }
      decision.site?.let { site ->
        lookupJob = scope.launch {
          val result = networkMutex.withLock {
            withContext(Dispatchers.IO) { lookup(site) }
          }
          if (run != generation || !_state.value.active) return@launch
          selectedListing = result.first?.let { p ->
            withContext(Dispatchers.IO) { runCatching { listings.statusFor(site, p) }.getOrDefault(ListingStatus.Unknown) }
          } ?: ListingStatus.Unknown
          if (run != generation || !_state.value.active) return@launch
          _state.update { it.copy(assessment = result.first, loading = false, message = result.second) }
          publishHud()
        }
      }
    } else if (decision.site == null && decision.kind == TargetKind.LOCATION_WEAK) {
      _state.update { it.copy(message = "Location is weak. Step outside and enable precise location.") }
    }
    publishHud()
  }

  private fun lookup(site: AddressSite): Pair<PropertyAssessment?, String> {
    cache[site.fullAddress]?.let { return it to "BC Assessment · cached this session" }
    return try {
      val matches = assessments.search(site).filter { AddressIdentity.matches(site, it.label) }
      if (matches.any { it.isMultiUnit }) return null to "Multi-unit building. Each unit has its own assessment."
      val match = matches.distinctBy { it.oid }.singleOrNull()
          ?: return null to "No unique assessment address match. Tap Rescan to retry."
      val info = assessments.propertyInfo(match.oid)
          ?: return null to "Assessment unavailable. Tap Rescan to retry."
      if (!AddressIdentity.matches(site, info.address)) return null to "Assessment address differs. Record withheld."
      cache[site.fullAddress] = info
      info to "BC Assessment · assessed value, not market price"
    } catch (error: Exception) {
      Log.w("HouseHud", "Assessment lookup failed", error)
      null to "Assessment service unavailable. Tap Rescan to retry."
    }
  }

  private fun publishHud() {
    if (!_state.value.active) return
    val s = _state.value
    val key = "${s.target.kind}|${s.target.site?.fullAddress}|${s.assessment?.totalValue}|${s.loading}|${s.message}"
    if (key == lastHudKey) return
    lastHudKey = key
    val p = s.assessment
    if (p != null) {
      val site = s.target.site
      val fix = locator.fix
      val distance = if (site != null && fix != null) HouseLocator.distanceM(fix.latitude, fix.longitude,
          site.latitude, site.longitude).toInt() else null
      val bitmap = HudRenderer.render(p, selectedListing, distance, s.target.kind.label)
      send { flexBox(direction = Direction.COLUMN, gap = 8) {
        image(bitmap = bitmap, sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
        controls()
      } }
    } else {
      send { flexBox(direction = Direction.COLUMN, gap = 8) {
        flexBox(padding = 20, gap = 8, background = FlexBoxBackground.CARD) {
          text(s.target.kind.label, style = TextStyle.BODY, color = TextColor.SECONDARY)
          text(s.target.site?.fullAddress?.substringBefore(",") ?: "BC House HUD", style = TextStyle.HEADING)
          text(s.message, style = TextStyle.BODY, color = TextColor.SECONDARY)
        }
        controls()
      } }
    }
  }

  private fun FlexBoxScope.controls() {
    flexBox(direction = Direction.ROW, gap = 8, alignment = Alignment.CENTER) {
      buttonGroup {
        button("Prev", style = ButtonStyle.SECONDARY, iconName = IconName.ARROW_LEFT, onClick = { scope.launch { step(-1) } })
        button("Rescan", style = ButtonStyle.PRIMARY, iconName = IconName.TWO_ARROWS_CLOCKWISE, onClick = { scope.launch { rescanNow() } })
        button("Next", style = ButtonStyle.SECONDARY, iconName = IconName.ARROW_RIGHT, onClick = { scope.launch { step(1) } })
      }
    }
  }
}
