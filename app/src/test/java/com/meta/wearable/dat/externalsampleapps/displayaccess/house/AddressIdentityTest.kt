package com.meta.wearable.dat.externalsampleapps.displayaccess.house
import org.junit.Assert.*
import org.junit.Test
class AddressIdentityTest {
  private val site = AddressSite("2892 W 35TH AVE, VANCOUVER", "2892", "35TH", "AVE", "W", true, "VANCOUVER", 49.2, -123.1)
  @Test fun acceptsDirectionBeforeOrAfterStreet() {
    assertTrue(AddressIdentity.matches(site, "2892 35TH AVE W VANCOUVER V6N 2M4"))
    assertTrue(AddressIdentity.matches(site, "2892 W 35TH AVENUE VANCOUVER"))
  }
  @Test fun rejectsWrongNumberTownOrDirection() {
    assertFalse(AddressIdentity.matches(site, "2894 35TH AVE W VANCOUVER"))
    assertFalse(AddressIdentity.matches(site, "2892 35TH AVE W VICTORIA"))
    assertFalse(AddressIdentity.matches(site, "2892 35TH AVE E VANCOUVER"))
  }
  @Test fun rejectsSubstringMatches() {
    assertFalse(AddressIdentity.matches(site, "12892 35TH AVE W VANCOUVER"))
    assertFalse(AddressIdentity.matches(site, "2892 135TH AVE W VANCOUVER"))
  }
}
