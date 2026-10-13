package com.meta.wearable.dat.externalsampleapps.displayaccess.house

/** Never let a fuzzy search result silently substitute another assessment address. */
object AddressIdentity {
  fun matches(site: AddressSite, label: String): Boolean {
    if (site.locality.isBlank() || site.streetName.isBlank()) return false
    val value = TargetResolver.normalizeStreet(label)
    val civic = TargetResolver.normalizeNumber(site.civicNumber)
    val number = Regex("""^(\d{1,6}[A-Z]?)(?:\s|$)""").find(value)?.groupValues?.get(1) ?: return false
    if (number != civic) return false
    val tail = value.removePrefix(number).trim()
    val street = TargetResolver.normalizeStreet(site.streetName + " " + site.streetType)
    val direction = TargetResolver.normalizeStreet(site.streetDirection)
    val town = TargetResolver.normalizeStreet(site.locality)
    val addressPart = if (direction.isEmpty()) listOf(street) else listOf("$direction $street", "$street $direction")
    return addressPart.any { tail.startsWith("$it $town") &&
        (tail.length == it.length + town.length + 1 || tail[it.length + town.length + 1] == ' ') }
  }
}
