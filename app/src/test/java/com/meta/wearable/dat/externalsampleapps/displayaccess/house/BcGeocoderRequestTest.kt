package com.meta.wearable.dat.externalsampleapps.displayaccess.house
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
class BcGeocoderRequestTest {
  @Test fun nearbySearchRequestsBuildingsInsteadOfHundredsOfStrataUnits() {
    val query = CompletableFuture<String>()
    val server = ServerSocket(0).apply { soTimeout = 3000 }
    val worker = Thread {
      try {
        server.accept().use { socket ->
          socket.soTimeout = 3000
          val reader = socket.getInputStream().bufferedReader()
          val path = reader.readLine().split(' ')[1]
          while (!reader.readLine().isNullOrEmpty()) { }
          val body = """{"features":[]}"""
          socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n" + body).toByteArray())
          query.complete(path.substringAfter('?'))
        }
      } catch (error: Exception) { query.completeExceptionally(error) }
    }.apply { isDaemon = true; start() }
    try {
      BcGeocoderClient("http://127.0.0.1:${server.localPort}").sitesNear(49.27,-123.13,80,50)
      val parameters = query.get(3,TimeUnit.SECONDS).split('&')
      assertTrue(parameters.contains("excludeUnits=true"))
      assertTrue(parameters.contains("onlyCivic=true"))
    } finally { server.close(); worker.join(3000) }
  }
}
