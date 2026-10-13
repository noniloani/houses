package com.meta.wearable.dat.externalsampleapps.displayaccess.house
import org.junit.Assert.*
import org.junit.Test
import com.meta.wearable.dat.camera.types.StreamError
class VisionErrorPolicyTest {
  @Test fun informationalErrorKeepsVisionRunning() {
    assertFalse(VisionErrorPolicy.mustStop(StreamError.STREAM_ERROR))
  }
  @Test fun criticalAndPermissionErrorsStopVision() {
    assertTrue(VisionErrorPolicy.mustStop(StreamError.CRITICAL_STREAM_ERROR))
    assertTrue(VisionErrorPolicy.mustStop(StreamError.PERMISSIONS_DENIED))
  }
}
