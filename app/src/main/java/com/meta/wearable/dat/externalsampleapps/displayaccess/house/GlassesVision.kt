package com.meta.wearable.dat.externalsampleapps.displayaccess.house

import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.removeCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.session.DeviceSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

data class VisionState(
    val label: String = "Camera off",
    val streaming: Boolean = false,
    val bitmap: Bitmap? = null,
    val tags: List<TextTag> = emptyList(),
    val error: String? = null,
)

/** Owns a camera capability and bounded on-device OCR for one scan session. */
class GlassesVision(
    private val parentScope: CoroutineScope,
    private val onObservation: (List<TextTag>, Long) -> Unit,
    private val onAvailability: (Boolean) -> Unit,
) {
  private val _state = MutableStateFlow(VisionState())
  val state = _state.asStateFlow()
  private var camera: Camera? = null
  private var session: DeviceSession? = null
  private var job: Job? = null
  private var generation = 0L
  private var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null

  fun unavailable(reason: String) {
    stop()
    _state.value = VisionState(label = "Camera unavailable", error = reason)
  }

  fun start(deviceSession: DeviceSession) {
    stop()
    val run = ++generation
    session = deviceSession
    val scanJob = SupervisorJob(parentScope.coroutineContext[Job])
    job = scanJob
    val scope = CoroutineScope(parentScope.coroutineContext + scanJob)
    val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer = reader
    val busy = AtomicBoolean(false)
    var lastFrameAt = 0L
    _state.value = VisionState(label = "Starting camera")
    deviceSession.addCamera(StreamConfiguration(videoQuality = VideoQuality.MEDIUM,
        frameRate = 7, compressVideo = false)).fold(
      onSuccess = { added ->
        camera = added
        val stream = added.stream
        scope.launch {
          var wasActive = false
          stream.state.collect { state ->
            if (run != generation) return@collect
            val available = state == StreamState.STREAMING
            if (state != StreamState.STOPPED) wasActive = true
            onAvailability(available)
            _state.update { it.copy(streaming = available,
                bitmap = if (available) it.bitmap else null,
                tags = if (available) it.tags else emptyList(),
                label = when (state) {
                  StreamState.STREAMING -> "Camera live"
                  StreamState.PAUSED -> "Camera paused"
                  StreamState.CLOSED, StreamState.STOPPED -> if (wasActive) "Camera stopped" else "Starting camera"
                  else -> "Starting camera"
                }) }
          }
        }
        scope.launch {
          stream.errorStream.collect { error ->
            if (run == generation) {
              unavailable(error.description)
            }
          }
        }
        scope.launch(Dispatchers.Default) {
          stream.videoStream.collect { frame ->
            val now = SystemClock.elapsedRealtime()
            if (run != generation || !_state.value.streaming || now - lastFrameAt < 500 ||
                frame.isCompressed || frame.isCodecConfig || !busy.compareAndSet(false, true)) return@collect
            lastFrameAt = now
            // Copy synchronously while this SDK frame is owned by the collector.
            val bytes = ByteArray(frame.buffer.remaining())
            frame.buffer.duplicate().get(bytes)
            val width = frame.width
            val height = frame.height
            scope.launch(Dispatchers.Default) {
              try {
                val pixels = I420.toArgb(bytes, width, height)
                val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                val result = reader.process(InputImage.fromBitmap(bitmap, 0)).await()
                val tags = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                  val box = line.boundingBox ?: return@mapNotNull null
                  TextTag(line.text, box.left.toFloat() / width, box.top.toFloat() / height,
                      box.right.toFloat() / width, box.bottom.toFloat() / height)
                }
                withContext(Dispatchers.Main.immediate) {
                  if (run == generation && _state.value.streaming && SystemClock.elapsedRealtime() - now < 1500) {
                    _state.update { it.copy(bitmap = bitmap, tags = tags, error = null) }
                    onObservation(tags, now)
                  }
                }
              } catch (cancelled: CancellationException) {
                throw cancelled
              } catch (error: Exception) {
                withContext(Dispatchers.Main.immediate) {
                  if (run == generation) unavailable(error.message ?: "Camera analysis failed")
                }
              } finally { busy.set(false) }
            }
          }
        }
        stream.start().onFailure { error, _ -> unavailable(error.description) }
      },
      onFailure = { error, _ -> unavailable(error.description) },
    )
  }

  fun stop() {
    generation++
    job?.cancel(); job = null
    runCatching { camera?.stop() }
    runCatching { session?.removeCamera() }
    camera = null; session = null
    recognizer?.close(); recognizer = null
    _state.value = VisionState()
    onAvailability(false)
  }
}
