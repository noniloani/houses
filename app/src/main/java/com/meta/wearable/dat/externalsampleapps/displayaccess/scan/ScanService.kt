package com.meta.wearable.dat.externalsampleapps.displayaccess.scan

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.meta.wearable.dat.externalsampleapps.displayaccess.MainActivity
import com.meta.wearable.dat.externalsampleapps.displayaccess.R

/** Keeps glasses transport and phone location alive while the wearer pockets/locks the phone. */
class ScanService : Service() {
  companion object {
    private const val CHANNEL = "house_scan"
    private const val STOP = "com.nonilion.bchousehud.STOP_SCAN"
    private var requested = false
    private var stopScan: (() -> Unit)? = null
    fun start(context: Context, onStop: () -> Unit) {
      stopScan = onStop
      if (requested) return
      context.startForegroundService(Intent(context, ScanService::class.java))
      requested = true
    }
    fun stop(context: Context) {
      if (!requested) return
      requested = false
      context.startService(Intent(context, ScanService::class.java).setAction(STOP))
    }
  }
  private var wakeLock: PowerManager.WakeLock? = null
  override fun onBind(intent: Intent?): IBinder? = null
  override fun onCreate() {
    super.onCreate()
    getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL, "House scanning", NotificationManager.IMPORTANCE_LOW))
  }
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val stop = PendingIntent.getService(this, 1, Intent(this, ScanService::class.java).setAction(STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("House lens is scanning")
        .setContentText("Glasses camera + location · tap Stop to end")
        .setContentIntent(open).setOngoing(true).addAction(0,"Stop",stop).build()
    // Enter foreground even if STOP raced a pending start: satisfy Android's service contract.
    try {
      startForeground(201, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
          ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    } catch (error: Exception) {
      requested = false
      stopScan?.invoke(); stopScan = null
      stopSelf(); return START_NOT_STICKY
    }
    if (intent?.action == STOP) {
      requested = false
      val callback = stopScan; stopScan = null; callback?.invoke()
      stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
      return START_NOT_STICKY
    }
    if (wakeLock == null) wakeLock = getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HouseLens:scan").apply { acquire(60 * 60 * 1000L) }
    // A process restart cannot restore SDK capabilities or camera consent implicitly.
    return START_NOT_STICKY
  }
  override fun onDestroy() {
    wakeLock?.let { if(it.isHeld) it.release() }; wakeLock = null
    requested = false
    val callback = stopScan; stopScan = null; callback?.invoke()
    super.onDestroy()
  }
}
