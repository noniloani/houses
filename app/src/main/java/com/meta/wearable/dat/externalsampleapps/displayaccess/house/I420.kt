package com.meta.wearable.dat.externalsampleapps.displayaccess.house
/** Meta DAT 1.0.1 decoded frames use planar YUV420 (codec format 19). */
object I420 {
  fun toArgb(data: ByteArray, width: Int, height: Int): IntArray {
    require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0)
    require(data.size == width * height * 3 / 2) { "Unsupported camera frame layout" }
    val area = width * height
    return IntArray(area) { i ->
      val uv = (i / width / 2) * (width / 2) + (i % width / 2)
      val y = (data[i].toInt() and 255) - 16
      val u = (data[area + uv].toInt() and 255) - 128
      val v = (data[area + area / 4 + uv].toInt() and 255) - 128
      val r = ((298 * y + 409 * v + 128) shr 8).coerceIn(0, 255)
      val g = ((298 * y - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
      val b = ((298 * y + 516 * u + 128) shr 8).coerceIn(0, 255)
      (255 shl 24) or (r shl 16) or (g shl 8) or b
    }
  }
}
