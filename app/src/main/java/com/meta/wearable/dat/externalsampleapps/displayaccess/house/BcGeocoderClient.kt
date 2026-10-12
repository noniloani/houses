package com.meta.wearable.dat.externalsampleapps.displayaccess.house

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** A civic address site from the BC Address Geocoder (DataBC, free and public). */
data class AddressSite(
    val fullAddress: String,
    val civicNumber: String,
    val streetName: String,
    val streetType: String,
    val streetDirection: String,
    val directionIsPrefix: Boolean,
    val locality: String,
    val latitude: Double,
    val longitude: Double,
)

/** Thin client for https://geocoder.api.gov.bc.ca (no key needed for light use). */
class BcGeocoderClient(private val baseUrl: String = "https://geocoder.api.gov.bc.ca") {

  /** Civic address sites near a point, closest first. Blocking: call from Dispatchers.IO. */
  fun sitesNear(latitude: Double, longitude: Double, maxDistanceM: Int, maxResults: Int): List<AddressSite> {
    val url =
        "$baseUrl/sites/near.json?point=$longitude,$latitude&maxDistance=$maxDistanceM" +
            "&maxResults=$maxResults&outputSRS=4326&locationDescriptor=parcelPoint"
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 15_000
    conn.setRequestProperty("Accept", "application/json")
    return try {
      if (conn.responseCode != 200) return emptyList()
      val body = conn.inputStream.bufferedReader().use { it.readText() }
      parseSites(body)
    } finally {
      conn.disconnect()
    }
  }

  companion object {
    fun parseSites(json: String): List<AddressSite> {
      val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
      return (0 until features.length()).mapNotNull { i ->
        val f = features.getJSONObject(i)
        val p = f.optJSONObject("properties") ?: return@mapNotNull null
        val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
        val civic = p.optString("civicNumber").takeIf { it.isNotBlank() && it != "null" }
            ?: return@mapNotNull null
        AddressSite(
            fullAddress = p.optString("fullAddress"),
            civicNumber = civic + p.optString("civicNumberSuffix").trim(),
            streetName = p.optString("streetName"),
            streetType = p.optString("streetType"),
            streetDirection = p.optString("streetDirection"),
            directionIsPrefix = p.optString("isStreetDirectionPrefix") == "true",
            locality = p.optString("localityName"),
            latitude = coords.getDouble(1),
            longitude = coords.getDouble(0),
        )
      }
    }
  }
}
