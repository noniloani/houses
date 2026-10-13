package com.meta.wearable.dat.externalsampleapps.displayaccess.scan
import org.junit.Assert.*
import org.junit.Test
class ScanLeaseTest {
  @Test fun delayedStopAndDestructionCannotStopNewScan() {
    val lease = ScanLease(); var stopped = 0
    val old = lease.begin { stopped++ }
    assertEquals(old, lease.revoke())
    val fresh = lease.begin { stopped++ }
    lease.finish(old)
    assertEquals(fresh, lease.token); assertEquals(0, stopped)
    lease.finish(fresh); lease.finish(fresh)
    assertNull(lease.token); assertEquals(1, stopped)
  }
  @Test fun programmaticStopDoesNotRepeatViewModelStop() {
    val lease = ScanLease(); var stopped = 0
    val token = lease.begin { stopped++ }
    lease.revoke(); lease.finish(token)
    assertEquals(0, stopped)
  }
}
