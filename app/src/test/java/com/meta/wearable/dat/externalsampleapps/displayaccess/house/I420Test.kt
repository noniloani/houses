package com.meta.wearable.dat.externalsampleapps.displayaccess.house
import org.junit.Assert.*
import org.junit.Test
class I420Test {
  @Test fun neutralBlackAndWhiteDecodeWithoutSwappingChroma() {
    val pixels = I420.toArgb(byteArrayOf(16, 235.toByte(), 16, 235.toByte(), 128.toByte(), 128.toByte()), 2, 2)
    assertEquals(0xff000000.toInt(), pixels[0])
    assertEquals(0xffffffff.toInt(), pixels[1])
  }
  @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedFrames() {
    I420.toArgb(ByteArray(3), 2, 2)
  }
  @Test fun redUsesUThenVPlanes() {
    val pixels = I420.toArgb(byteArrayOf(81, 81, 81, 81, 90, 240.toByte()), 2, 2)
    assertTrue((pixels[0] shr 16 and 255) > 240)
    assertTrue((pixels[0] and 255) < 10)
  }
}
