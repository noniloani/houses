package com.meta.wearable.dat.externalsampleapps.displayaccess.display

import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Retained by DisplayViewModel, so recreated Activities deliver to the same request. */
class CameraPermissionRequests {
  private val mutex = Mutex()
  private var pending: CancellableContinuation<PermissionStatus>? = null

  suspend fun request(launch: () -> Unit): PermissionStatus = mutex.withLock {
    suspendCancellableCoroutine { continuation ->
      pending = continuation
      continuation.invokeOnCancellation { if (pending === continuation) pending = null }
      try { launch() }
      catch (error: Exception) {
        if (pending === continuation) pending = null
        continuation.resumeWithException(error)
      }
    }
  }

  fun complete(status: PermissionStatus) {
    val continuation = pending
    pending = null
    if (continuation?.isActive == true) continuation.resume(status)
  }
}
