package com.meta.wearable.dat.externalsampleapps.displayaccess.display

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Main-thread-owned latest-value queue: one send in flight and replay on each attachment. */
class LatestContentSender<T : Any>(scope: CoroutineScope, private val onFailure: () -> Unit = {}, private val send: suspend (T) -> Boolean) {
  private val signal = Channel<Unit>(Channel.CONFLATED)
  private var latest: T? = null
  private var version = 0L
  private var delivered = -1L
  private var connection = 0L
  private var available = false
  private val worker = scope.launch {
    for (ignored in signal) {
      while (available && latest != null && version != delivered) {
        val content = latest ?: break
        val sendingVersion = version
        val sendingConnection = connection
        val success = send(content)
        if (sendingConnection != connection) continue
        if (success) delivered = sendingVersion
        else { available = false; onFailure(); break }
      }
    }
  }
  fun submit(content: T) { latest = content; version++; signal.trySend(Unit) }
  fun ready() { connection++; available = true; delivered = -1; signal.trySend(Unit) }
  fun disconnected() { connection++; available = false }
  fun clear() { disconnected(); latest = null; version++; delivered = -1 }
  fun close() { clear(); worker.cancel(); signal.close() }
}
