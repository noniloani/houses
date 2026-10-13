package com.meta.wearable.dat.externalsampleapps.displayaccess.display
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class LatestContentSenderTest {
  @Test fun holdsContentUntilDisplayReadyAndReplaysAfterReconnect() = runBlocking {
    val sent = mutableListOf<String>()
    val sender = LatestContentSender<String>(this) { sent.add(it); true }
    sender.submit("house"); yield()
    assertTrue(sent.isEmpty())
    sender.ready(); yield()
    assertEquals(listOf("house"), sent)
    sender.disconnected(); sender.submit("unit"); yield()
    assertEquals(listOf("house"), sent)
    sender.ready(); yield()
    assertEquals(listOf("house", "unit"), sent)
    sender.disconnected(); sender.ready(); yield()
    assertEquals(listOf("house", "unit", "unit"), sent)
    sender.close()
  }
  @Test fun replacesQueuedUpdatesWithoutConcurrentSends() = runBlocking {
    val sent = mutableListOf<String>()
    val gate = CompletableDeferred<Unit>()
    val sender = LatestContentSender<String>(this) { sent.add(it); if(it=="a") gate.await(); true }
    sender.ready(); sender.submit("a"); yield()
    sender.submit("b"); sender.submit("c"); yield()
    gate.complete(Unit); yield()
    assertEquals(listOf("a", "c"), sent)
    sender.close()
  }
  @Test fun failedSendRetainsPayloadForRecovery() = runBlocking {
    var tries=0
    val sender = LatestContentSender<String>(this) { tries++; tries > 1 }
    sender.ready(); sender.submit("unit"); yield()
    assertEquals(1, tries)
    sender.ready(); yield()
    assertEquals(2, tries)
    sender.close()
  }
}
