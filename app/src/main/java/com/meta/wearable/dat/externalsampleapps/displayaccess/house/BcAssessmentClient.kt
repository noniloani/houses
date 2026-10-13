package com.meta.wearable.dat.externalsampleapps.displayaccess.house

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray

/** One address match from BC Assessment's address search. */
data class AssessmentMatch(val label: String, val oid: String, val isMultiUnit: Boolean, val groupId: String? = null)

/**
 * Looks up properties on bcassessment.ca the same way its public search page does:
 * GET the home page for a session cookie, search by address, then fetch the property
 * information fragment. Only call this for a house the user is actually looking at, and
 * cache results; it is not a bulk data feed.
 *
 * All calls are blocking. Run them on Dispatchers.IO.
 */
class BcAssessmentClient(private val baseUrl: String = "https://www.bcassessment.ca") {

  private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
  private var bootstrapped = false

  private fun bootstrap() {
    if (bootstrapped) return
    request("$baseUrl/", referer = null)
    bootstrapped = true
  }

  /** Address matches for a BC Geocoder site, filtered to its town when possible. */
  fun search(site: AddressSite): List<AssessmentMatch> {
    bootstrap()
    val queries = queriesFor(site)
    for (q in queries) {
      val body =
          request(
              "$baseUrl/Property/Search/GetByAddress/?addr=${enc(q)}",
              referer = "$baseUrl/",
          ) ?: continue
      val matches = parseMatches(body)
      if (matches.isEmpty()) continue
      val town = site.locality.uppercase()
      val inTown = matches.filter { town.isNotBlank() && it.label.uppercase().contains(town) }
      return inTown.ifEmpty { matches }
    }
    return emptyList()
  }

  /** Same selected-building endpoint used by BC Assessment's public unit selector. */
  fun units(groupId: String): UnitDirectory {
    require(Regex("\\d+").matches(groupId)) { "Invalid building ID" }
    bootstrap()
    val body = request("$baseUrl/Property/Search/GetSubUnits/${enc(groupId)}?multiunitId=${enc(groupId)}", "$baseUrl/")
        ?: throw java.io.IOException("Unit list unavailable")
    return UnitDirectory.parse(body)
  }

  /** The parsed assessment for a BC Assessment property id, or null. */
  fun propertyInfo(oid: String): PropertyAssessment? {
    bootstrap()
    val html =
        request(
            "$baseUrl/Property/PropertyInformation",
            referer = "$baseUrl/Property/Info/$oid",
            form = "oid=${enc(oid)}",
        ) ?: return null
    return BcAssessmentParser.parse(html)
  }

  private fun request(url: String, referer: String?, form: String? = null): String? {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.instanceFollowRedirects = false
    conn.connectTimeout = 10_000
    conn.readTimeout = 20_000
    conn.setRequestProperty("User-Agent", USER_AGENT)
    conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
    referer?.let { conn.setRequestProperty("Referer", it) }
    cookieHeader(url)?.let { conn.setRequestProperty("Cookie", it) }
    if (form != null) {
      conn.requestMethod = "POST"
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
      conn.outputStream.use { it.write(form.toByteArray()) }
    }
    return try {
      val code = conn.responseCode
      storeCookies(url, conn)
      if (code != 200) return null
      conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
      conn.disconnect()
    }
  }

  private fun cookieHeader(url: String): String? {
    val list = cookies.cookieStore.get(URL(url).toURI())
    return list.takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.name}=${it.value}" }
  }

  private fun storeCookies(url: String, conn: HttpURLConnection) {
    cookies.put(URL(url).toURI(), conn.headerFields.filterKeys { it != null })
  }

  companion object {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0 Mobile Safari/537.36 BcHouseHud/1.0"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /**
     * BC Assessment wants "2892 35TH AVE W" style input. Try with the direction first, then
     * without it, since the search is picky.
     */
    fun queriesFor(site: AddressSite): List<String> {
      val street = listOf(site.streetName, site.streetType).filter { it.isNotBlank() }.joinToString(" ")
      val dir = site.streetDirection.trim()
      val withDir =
          when {
            dir.isEmpty() -> null
            site.directionIsPrefix -> "${site.civicNumber} $dir $street"
            else -> "${site.civicNumber} $street $dir"
          }
      return listOfNotNull(withDir, "${site.civicNumber} $street").map { it.lowercase() }.distinct()
    }

    fun parseMatches(json: String): List<AssessmentMatch> =
        runCatching {
              val arr = JSONArray(json)
              (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val label = o.optString("label")
                val value = o.optString("value")
                val multiUnit = label.contains("all units", ignoreCase = true)
                when {
                  label.equals("No results", true) -> null
                  multiUnit -> AssessmentMatch(label.substringBefore(" (").trim(), "", true,
                      o.optString("gid").takeUnless { it.isBlank() || it == "null" || it == "0" })
                  value.isBlank() || value == "0" -> null
                  else -> AssessmentMatch(label, value, false)
                }
              }
            }
            .getOrDefault(emptyList())
  }
}
