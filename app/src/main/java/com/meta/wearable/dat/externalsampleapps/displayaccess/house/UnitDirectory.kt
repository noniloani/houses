package com.meta.wearable.dat.externalsampleapps.displayaccess.house

import org.json.JSONObject

data class UnitRecord(val oid: String, val address: String, val number: String)
data class UnitDirectory(val units: List<UnitRecord>, val total: Int, val truncated: Boolean) {
  companion object {
    private val unitAddress = Regex("""^([A-Z0-9]+)\s*-\s*(.+)$""")
    fun parse(json: String): UnitDirectory {
      val root = JSONObject(json)
      val rows = root.optJSONArray("Units") ?: return UnitDirectory(emptyList(),0,false)
      val units = (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONObject(i) ?: return@mapNotNull null
        val oid = row.optString("Oa000_OID").takeUnless { it.isBlank() || it == "null" || it == "0" } ?: return@mapNotNull null
        val address = row.optString("Address").trim()
        val number = unitAddress.find(address.uppercase())?.groupValues?.get(1) ?: return@mapNotNull null
        UnitRecord(oid,address,number)
      }.distinctBy { it.oid }.sortedWith(compareBy<UnitRecord> { it.number.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.number })
      return UnitDirectory(units,root.optInt("TotalCount",units.size).coerceAtLeast(units.size),
          root.optBoolean("IsMoreThanLimit",false))
    }
    fun belongsTo(site: AddressSite, address: String): Boolean {
      val building = unitAddress.find(address.uppercase())?.groupValues?.get(2) ?: return false
      return AddressIdentity.matches(site, building)
    }
    fun sameUnit(expected: String, actual: String): Boolean {
      val a = TargetResolver.normalizeStreet(expected)
      val b = TargetResolver.normalizeStreet(actual)
      return a.isNotBlank() && (a == b || b.startsWith("$a "))
    }
  }
}
class UnitBrowser {
  var building: AddressSite? = null; private set
  var directory = UnitDirectory(emptyList(),0,false); private set
  var page = 0; private set
  val isOpen get() = building != null
  val visible get() = directory.units.drop(page * 4).take(4)
  val pageCount get() = ((directory.units.size + 3) / 4).coerceAtLeast(1)
  fun open(site: AddressSite, units: UnitDirectory) { building = site; directory = units; page = 0 }
  fun next() { page = (page + 1).coerceAtMost(pageCount - 1) }
  fun previous() { page = (page - 1).coerceAtLeast(0) }
  fun choose(oid: String): UnitRecord? = visible.firstOrNull { it.oid == oid }
  fun close() { building = null; directory = UnitDirectory(emptyList(),0,false); page = 0 }
}
