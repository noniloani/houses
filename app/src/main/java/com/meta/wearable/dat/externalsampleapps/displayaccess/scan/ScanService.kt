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
    private const val TOKEN = "scan_token"
    private val lease = ScanLease()
    fun start(context: Context, onStop: () -> Unit) {
      val alreadyRunning = lease.token != null
      val token = lease.begin(onStop)
      if (alreadyRunning) return
      try { context.startForegroundService(Intent(context, ScanService::class.java).putExtra(TOKEN, token)) }
      catch (error: Exception) { lease.finish(token); throw error }
    }
    fun stop(context: Context) {
      val token = lease.revoke() ?: return
      context.startService(Intent(context, ScanService::class.java).setAction(STOP).putExtra(TOKEN, token))
    }
  }
  private var owner = 0L
  private var wakeLock: PowerManager.WakeLock? = null
  override fun onBind(intent: Intent?): IBinder? = null
  override fun onCreate() {
    super.onCreate()
    getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL, "House scanning", NotificationManager.IMPORTANCE_LOW))
  }
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val incoming = intent?.getLongExtra(TOKEN, 0L) ?: 0L
    val active = lease.token
    if (active != null) owner = active
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val stop = PendingIntent.getService(this, 1, Intent(this, ScanService::class.java).setAction(STOP).putExtra(TOKEN, owner),
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
      lease.finish(owner)
      stopSelf(); return START_NOT_STICKY
    }
    if (intent?.action == STOP) {
      if (lease.token != null && lease.token != incoming) return START_NOT_STICKY
      lease.finish(incoming)
      stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
      return START_NOT_STICKY
    }
    if (lease.token == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
    if (wakeLock == null) wakeLock = getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HouseLens:scan").apply { acquire(60 * 60 * 1000L) }
    // A process restart cannot restore SDK capabilities or camera consent implicitly.
    return START_NOT_STICKY
  }
  override fun onDestroy() {
    wakeLock?.let { if(it.isHeld) it.release() }; wakeLock = null
    lease.finish(owner)
    super.onDestroy()
  }
}
