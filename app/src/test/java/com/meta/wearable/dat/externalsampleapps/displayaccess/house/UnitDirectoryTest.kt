package com.meta.wearable.dat.externalsampleapps.displayaccess.house
import org.junit.Assert.*
import org.junit.Test
class UnitDirectoryTest {
  private val building = AddressSite("1000 BEACH AVE, VANCOUVER", "1000", "BEACH", "AVE", "", false, "VANCOUVER",49.27,-123.13)
  @Test fun keepsBuildingGroupIdForAllUnitsLookup() {
    val result = BcAssessmentClient.parseMatches("""[{"label":"1000 BEACH AVE VANCOUVER (select to see all units...)","value":"","gid":28270}]""")
    assertEquals("28270", result.single().groupId)
  }
  @Test fun parsesUnitsAndValidatesSelectedUnitAgainstBuilding() {
    val units = UnitDirectory.parse("""{"TotalCount":115,"IsMoreThanLimit":false,"Units":[{"Oa000_OID":"oid111","Address":"111-1000 BEACH AVE VANCOUVER"},{"Oa000_OID":"oid201","Address":"201-1000 BEACH AVE VANCOUVER"}]}""")
    assertEquals(115, units.total)
    assertEquals("111", units.units.first().number)
    assertTrue(units.units.all { UnitDirectory.belongsTo(building,it.address) })
    assertFalse(UnitDirectory.belongsTo(building,"111-1001 BEACH AVE VANCOUVER"))
    assertTrue(UnitDirectory.sameUnit("111-1000 BEACH AVE VANCOUVER", "111-1000 BEACH AVE VANCOUVER V6E 4M2"))
    assertFalse(UnitDirectory.sameUnit("111-1000 BEACH AVE VANCOUVER", "112-1000 BEACH AVE VANCOUVER"))
  }
  @Test fun pagesWithoutLosingUnitsOrWrappingPastLastPage() {
    val list = (1..9).map { UnitRecord("oid$it", "$it-1000 BEACH AVE VANCOUVER", "$it") }
    val browser = UnitBrowser()
    browser.open(building, UnitDirectory(list,9,false))
    assertEquals(listOf("1","2","3","4"), browser.visible.map { it.number })
    browser.next()
    assertEquals(listOf("5","6","7","8"), browser.visible.map { it.number })
    browser.next(); browser.next()
    assertEquals(listOf("9"), browser.visible.map { it.number })
    assertNull(browser.choose("unrelated-oid"))
    assertEquals("9", browser.choose("oid9")?.number)
    browser.close()
    assertFalse(browser.isOpen)
  }
}
