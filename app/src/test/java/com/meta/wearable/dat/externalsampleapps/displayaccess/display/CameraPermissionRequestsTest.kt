package com.meta.wearable.dat.externalsampleapps.displayaccess.display
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class CameraPermissionRequestsTest {
  @Test fun resultDeliveredByNewCallbackResumesRetainedRequest() = runBlocking {
    val requests = CameraPermissionRequests()
    var launches = 0
    val waiting = async { requests.request { launches++ } }
    yield()
    val recreatedActivityCallback: (PermissionStatus) -> Unit = requests::complete
    recreatedActivityCallback(PermissionStatus.Granted)
    assertEquals(PermissionStatus.Granted, waiting.await())
    assertEquals(1, launches)
  }
  @Test fun canceledRequestCanBeRetried() = runBlocking {
    val requests = CameraPermissionRequests()
    val first = launch { requests.request {} }
    yield(); first.cancelAndJoin()
    val retry = async { requests.request {} }
    yield(); requests.complete(PermissionStatus.Granted)
    assertEquals(PermissionStatus.Granted, retry.await())
  }
  @Test fun denialReturnsWithoutHanging() = runBlocking {
    val requests = CameraPermissionRequests()
    val waiting = async { requests.request {} }
    yield(); requests.complete(PermissionStatus.Denied)
    assertEquals(PermissionStatus.Denied, waiting.await())
  }
}
