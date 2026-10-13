package com.meta.wearable.dat.externalsampleapps.displayaccess.house
import com.meta.wearable.dat.camera.types.StreamError
object VisionErrorPolicy {
  fun mustStop(error: StreamError) = error == StreamError.CRITICAL_STREAM_ERROR ||
      error == StreamError.PERMISSIONS_DENIED
}
