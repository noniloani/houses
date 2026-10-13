package com.meta.wearable.dat.externalsampleapps.displayaccess.house

import org.junit.Assert.*
import org.junit.Test

class TargetResolverTest {
  private val fix = Fix(49.25, -123.10, 5f, null)
  private fun site(number: String, street: String = "OAK") = AddressSite(
      "$number $street ST, VANCOUVER", number, street, "ST", "", false, "VANCOUVER", 49.2501, -123.10)
  private fun tag(text: String) = TextTag(text, .4f, .4f, .6f, .6f)
  @Test fun repeatedNumberLocksMatchingHouseInsteadOfNearest() {
    val r = TargetResolver()
    val sites = listOf(site("100"), site("102"))
    assertEquals(TargetKind.READING, r.observe(fix, sites, listOf(tag("102")), 1000).kind)
    r.observe(fix, sites, listOf(tag("102")), 1500)
    val result = r.observe(fix, sites, listOf(tag("102")), 2000)
    assertEquals(TargetKind.VISUAL, result.kind)
    assertEquals("102", result.site?.civicNumber)
  }
  @Test fun duplicateNumberOnDifferentStreetsIsUncertain() {
    val r = TargetResolver()
    val result = r.observe(fix, listOf(site("100"), site("100", "PINE")), listOf(tag("100")), 1000)
    assertEquals(TargetKind.UNCERTAIN, result.kind)
    assertNull(result.site)
  }
  @Test fun suffixIsNotDropped() {
    val r = TargetResolver()
    val result = r.observe(fix, listOf(site("100"), site("100A")), listOf(tag("100 A")), 1000)
    assertEquals("100A", result.site?.civicNumber)
  }
  @Test fun pricesAndStreetOrdinalsAreNotHouseNumbers() {
    val r = TargetResolver()
    val result = r.observe(fix, listOf(site("100"), site("35")), listOf(tag("$100"), tag("35TH AVE")), 1000)
    assertEquals(TargetKind.ESTIMATE, result.kind)
  }
  @Test fun matchingExpiresAndCameraInterruptionClearsLock() {
    val r = TargetResolver()
    val sites = listOf(site("100"))
    for (now in listOf(1000L, 1500L, 2000L)) r.observe(fix, sites, listOf(tag("100")), now)
    assertEquals(TargetKind.LAST_VISUAL, r.current(fix, sites, 4500).kind)
    assertEquals(TargetKind.ESTIMATE, r.current(fix, sites, 7500).kind)
    assertEquals(TargetKind.ESTIMATE, r.current(fix, sites, 2000, cameraActive = false).kind)
  }
  @Test fun conflictingFrameImmediatelyRemovesVisualLabel() {
    val r = TargetResolver()
    val sites = listOf(site("100"), site("102"))
    for (now in listOf(1000L, 1500L, 2000L)) r.observe(fix, sites, listOf(tag("100")), now)
    assertEquals(TargetKind.READING, r.observe(fix, sites, listOf(tag("102")), 2500).kind)
  }
  @Test fun competingCentralNumbersAreAmbiguous() {
    val result = TargetResolver().observe(fix, listOf(site("100"), site("102")), listOf(tag("100"), tag("102")), 1000)
    assertEquals(TargetKind.UNCERTAIN, result.kind)
  }
  @Test fun poorLocationCannotLock() {
    val r = TargetResolver()
    val badFix = fix.copy(accuracyM = 80f)
    val sites = listOf(site("100"))
    for (now in listOf(1000L, 1500L, 2000L)) r.observe(badFix, sites, listOf(tag("100")), now)
    assertEquals(TargetKind.LOCATION_WEAK, r.current(badFix, sites, 2000).kind)
  }
  @Test fun manualSelectionSurvivesVisionUntilHoldExpires() {
    val r = TargetResolver()
    val sites = listOf(site("100"), site("102"))
    r.hold(sites[0], 1000)
    assertEquals("100", r.observe(fix, sites, listOf(tag("102")), 2000).site?.civicNumber)
    assertEquals(TargetKind.MANUAL, r.current(fix, sites, 2000).kind)
    assertEquals(TargetKind.ESTIMATE, r.current(fix, sites, 22000).kind)
  }
  @Test fun streetEvidenceDisambiguatesRepeatedNumbers() {
    val result = TargetResolver().observe(fix, listOf(site("100"), site("100", "PINE")), listOf(tag("100"), tag("PINE ST")), 1000)
    assertEquals("PINE", result.site?.streetName)
  }
  @Test fun refreshingCandidatesDoesNotCancelManualHold() {
    val r = TargetResolver()
    val chosen = site("100")
    r.hold(chosen, 1000)
    r.invalidateVisual()
    assertEquals(TargetKind.MANUAL, r.current(fix, emptyList(), 2000).kind)
    assertEquals("100", r.current(fix, emptyList(), 2000).site?.civicNumber)
  }
  @Test fun staleLocationCannotAcquireVisualMatch() {
    val r = TargetResolver()
    val stale = fix.copy(ageMs = 16000)
    for (now in listOf(1000L, 1500L, 2000L)) r.observe(stale, listOf(site("100")), listOf(tag("100")), now)
    assertEquals(TargetKind.LOCATION_WEAK, r.current(stale, listOf(site("100")), 2000).kind)
  }
  @Test fun duplicateDeliveryCannotCreateAThreeFrameLock() {
    val r = TargetResolver()
    val sites = listOf(site("100"))
    repeat(3) { r.observe(fix, sites, listOf(tag("100")), 1000) }
    assertEquals(TargetKind.READING, r.current(fix, sites, 1000).kind)
  }
  @Test fun truncatedCandidateListCannotEstablishUniqueness() {
    val r = TargetResolver()
    assertEquals(TargetKind.UNCERTAIN, r.observe(fix, listOf(site("100")), listOf(tag("100")), 1000, false).kind)
  }
}
