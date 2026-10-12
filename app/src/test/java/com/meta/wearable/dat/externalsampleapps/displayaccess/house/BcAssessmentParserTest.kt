package com.meta.wearable.dat.externalsampleapps.displayaccess.house

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BcAssessmentParserTest {

  private fun fixture() =
      javaClass.classLoader!!.getResource("bca_2892_35th_ave_w.html")!!.readText()

  @Test
  fun parsesRealPropertyInformationFragment() {
    val p = BcAssessmentParser.parse(fixture())
    assertNotNull(p)
    p!!
    assertEquals("2892 35TH AVE W VANCOUVER V6N 2M4", p.address)
    assertEquals(2_861_000L, p.totalValue)
    assertEquals(2_832_000L, p.landValue)
    assertEquals(29_000L, p.buildingValue)
    assertEquals(3_043_100L, p.previousTotalValue)
    assertEquals("1930", p.yearBuilt)
    assertEquals("4", p.bedrooms)
    assertEquals("2", p.baths)
    assertEquals("44.4 x 130 Ft", p.landSize)
    assertEquals(940 + 448 + 825, p.floorAreaSqFt)
    assertEquals(10, p.tenYearValues.size)
    assertEquals(2_861_000L, p.tenYearValues.last())
    assertFalse(p.hasRecentSale)
    assertEquals(-5.98, p.yearOverYearPct!!, 0.01)
  }

  @Test
  fun rejectsErrorPage() {
    assertNull(BcAssessmentParser.parse("<html><body>Object moved</body></html>"))
  }

  @Test
  fun buildsBcAssessmentQueries() {
    val site = AddressSite("2892 W 35th Ave, Vancouver, BC", "2892", "35th", "Ave", "W", false, "Vancouver", 49.24, -123.17)
    assertEquals(listOf("2892 35th ave w", "2892 35th ave"), BcAssessmentClient.queriesFor(site))
  }

  @Test
  fun parsesMatchesAndMultiUnit() {
    val m = BcAssessmentClient.parseMatches(
        """[{"label":"1055 CANADA PL VANCOUVER (select to see all units...)","value":"","gid":8882},""" +
            """{"label":"2892 35TH AVE W VANCOUVER","value":"QTAwMDAwMFFRUQ==","gid":null}]""")
    assertEquals(2, m.size)
    assertEquals(true, m[0].isMultiUnit)
    assertEquals("QTAwMDAwMFFRUQ==", m[1].oid)
    assertEquals(0, BcAssessmentClient.parseMatches("""[{"label":"No results","value":0,"gid":0}]""").size)
  }

  @Test
  fun ranksHouseInFrontOverCloserHouseBehind() {
    val me = Fix(49.24, -123.17, 5f, headingDeg = 0f) // facing north
    val north = AddressSite("N", "1", "A", "St", "", false, "X", 49.24020, -123.17)
    val south = AddressSite("S", "2", "A", "St", "", false, "X", 49.23990, -123.17)
    assertEquals("N", HouseLocator.rank(me, listOf(south, north)).first().fullAddress)
  }
}
