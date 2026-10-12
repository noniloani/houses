package com.meta.wearable.dat.externalsampleapps.displayaccess.house

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where the wearer is and which way they face (degrees from true north), when known. */
data class Fix(val latitude: Double, val longitude: Double, val accuracyM: Float, val headingDeg: Float?)

/**
 * Tracks the phone's GPS position and compass heading. The phone has to roughly face the same
 * way as the wearer (in hand, or in a chest pocket screen-out) for the heading to help; without
 * a heading we fall back to the nearest address.
 */
class HouseLocator(context: Context) : SensorEventListener, LocationListener {

  private val locationManager = context.getSystemService(LocationManager::class.java)
  private val sensorManager = context.getSystemService(SensorManager::class.java)
  @Volatile private var lastLocation: Location? = null
  @Volatile private var headingDeg: Float? = null
  private val rotation = FloatArray(9)
  private val orientation = FloatArray(3)

  val fix: Fix?
    get() = lastLocation?.let { Fix(it.latitude, it.longitude, it.accuracy, headingDeg) }

  @SuppressLint("MissingPermission") // Requested in MainActivity before the HUD can start.
  fun start() {
    runCatching {
      for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
        if (locationManager?.isProviderEnabled(provider) == true) {
          locationManager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
          locationManager.requestLocationUpdates(provider, 2_000L, 2f, this, Looper.getMainLooper())
        }
      }
    }
    sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
      sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
    }
  }

  fun stop() {
    runCatching { locationManager?.removeUpdates(this) }
    sensorManager?.unregisterListener(this)
  }

  override fun onLocationChanged(location: Location) {
    val current = lastLocation
    if (current == null || location.accuracy <= current.accuracy + 15 ||
        location.time - current.time > 10_000) {
      lastLocation = location
    }
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
    SensorManager.getRotationMatrixFromVector(rotation, event.values)
    SensorManager.getOrientation(rotation, orientation)
    var magnetic = Math.toDegrees(orientation[0].toDouble()).toFloat()
    lastLocation?.let {
      magnetic += GeomagneticField(
              it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), it.time)
          .declination
    }
    val trueHeading = (magnetic + 360f) % 360f
    // Light smoothing so the pick doesn't jump between houses.
    headingDeg = headingDeg?.let { smooth(it, trueHeading) } ?: trueHeading
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  companion object {
    private fun smooth(old: Float, new: Float): Float {
      var delta = new - old
      if (delta > 180) delta -= 360
      if (delta < -180) delta += 360
      return (old + delta * 0.2f + 360f) % 360f
    }

    /** Metres between two points (equirectangular, fine at street scale). */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
      val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
      val y = Math.toRadians(lat2 - lat1)
      return sqrt(x * x + y * y) * 6_371_000.0
    }

    /** Bearing in degrees from point 1 to point 2. */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
      val p1 = Math.toRadians(lat1)
      val p2 = Math.toRadians(lat2)
      val dl = Math.toRadians(lon2 - lon1)
      val y = sin(dl) * cos(p2)
      val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
      return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /**
     * Orders address sites by how likely the wearer is looking at them: inside a ~35 degree
     * cone of the heading first (nearest first), then everything else by distance.
     */
    fun rank(fix: Fix, sites: List<AddressSite>): List<AddressSite> {
      val heading = fix.headingDeg ?: return sites.sortedBy {
        distanceM(fix.latitude, fix.longitude, it.latitude, it.longitude)
      }
      return sites.sortedBy { site ->
        val d = distanceM(fix.latitude, fix.longitude, site.latitude, site.longitude)
        var off = abs(bearingDeg(fix.latitude, fix.longitude, site.latitude, site.longitude) - heading)
        if (off > 180) off = 360 - off
        val inCone = off <= 35 || d < 8
        (if (inCone) 0.0 else 10_000.0) + d + off * 0.4
      }
    }
  }
}
