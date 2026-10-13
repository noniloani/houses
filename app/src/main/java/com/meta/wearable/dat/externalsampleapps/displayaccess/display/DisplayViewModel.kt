/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.display

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.GuardedBy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.types.VideoCodec
import com.meta.wearable.dat.display.types.VideoPlayerState
import com.meta.wearable.dat.display.types.VideoSource
import com.meta.wearable.dat.display.views.ActionRole
import com.meta.wearable.dat.display.views.Alignment
import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.ContentScope
import com.meta.wearable.dat.display.views.CornerRadius
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.FlexBoxScope
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.ImageSize
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle
import com.meta.wearable.dat.display.views.VideoPlayer
import com.meta.wearable.dat.externalsampleapps.displayaccess.R
import com.meta.wearable.dat.externalsampleapps.displayaccess.SampleApp
import com.meta.wearable.dat.externalsampleapps.displayaccess.house.HouseHudController
import com.meta.wearable.dat.externalsampleapps.displayaccess.house.GlassesVision
import com.meta.wearable.dat.externalsampleapps.displayaccess.house.AddressSite
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import com.meta.wearable.dat.externalsampleapps.displayaccess.scan.ScanService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint(
    "AutoCloseableUse",
    "NavigatorCoroutineLaunchWithoutExceptionHandler",
)
class DisplayViewModel(
    application: Application,
) : AndroidViewModel(application) {

  private companion object {
    private const val TAG = "DisplayAccessVM"
    private const val TUTORIAL_VIDEO_URL =
        "https://github.com/facebook/meta-wearables-dat-android/raw/refs/heads/assets/video_266x150_faststart.mp4"

    private val carMaintenanceTutorials = listOf(
        CarMaintenanceTutorial(
            title = "Oil change",
            duration = "Easy • 45 min",
            imageRes = R.drawable.oil,
            iconRes = R.drawable.oil_square,
            steps =
                listOf(
                    CarMaintenanceTutorialStep(
                        description =
                            "Park on level ground and let the engine cool before opening the hood.",
                    ),
                    CarMaintenanceTutorialStep(
                        description =
                            "Drain the old oil, replace the filter, and tighten the drain plug.",
                    ),
                    CarMaintenanceTutorialStep(
                        description =
                            "Refill with fresh oil, run the engine briefly, and recheck the level.",
                    ),
                ),
        ),
        CarMaintenanceTutorial(
            title = "Fix a flat tire",
            duration = "Easy • 15 min",
            imageUri = "https://www.facebook.com/assets/wearables_dat_display/tire.png",
            iconImageUri = "https://www.facebook.com/assets/wearables_dat_display/tire_square.png",
            steps =
                listOf(
                    CarMaintenanceTutorialStep(
                        description =
                            "Park away from traffic, engage the brake, and place the wheel wedges.",
                    ),
                    CarMaintenanceTutorialStep(
                        description =
                            "Loosen the lug nuts slightly, raise the car, and remove the flat tire.",
                    ),
                    CarMaintenanceTutorialStep(
                        description =
                            "Mount the spare, tighten in a star pattern, and lower the vehicle.",
                    ),
                ),
        ),
        CarMaintenanceTutorial(
            title = "Replace headlight bulb",
            duration = "Very easy • 5 min",
            imageUri = "https://www.facebook.com/assets/wearables_dat_display/light.png",
            iconImageUri = "https://www.facebook.com/assets/wearables_dat_display/light_square.png",
            steps =
                listOf(
                    CarMaintenanceTutorialStep(
                        description =
                            "Open the rear access cover and disconnect the bulb connector.",
                    ),
                    CarMaintenanceTutorialStep(
                        description =
                            "Release the retaining clip, remove the old bulb, and insert the new one.",
                    ),
                    CarMaintenanceTutorialStep(
                        description =
                            "Reconnect power, close the cover, and verify the beam works properly.",
                    ),
                ),
        ),
        CarMaintenanceTutorial(
            title = "Check engine light",
            duration = "Hard • 2 hours",
            imageUri = "https://www.facebook.com/assets/wearables_dat_display/engine.png",
            iconImageUri =
                "https://www.facebook.com/assets/wearables_dat_display/engine_square.png",
            steps =
                listOf(
                    CarMaintenanceTutorialStep(
                        description =
                            "Check whether the light is steady or flashing, and stop driving if it is flashing.",
                    ),
                    CarMaintenanceTutorialStep(
                        description =
                            "Tighten the gas cap fully and look for obvious issues like low fluids or overheating.",
                    ),
                    CarMaintenanceTutorialStep(
                        description =
                            "Scan for diagnostic codes or schedule service if the light stays on after restarting.",
                    ),
                ),
        ),
        CarMaintenanceTutorial(
            title = "Change washer fluid",
            duration = "Very easy • 3 min",
            imageUri = "https://www.facebook.com/assets/wearables_dat_display/washer.png",
            iconImageUri =
                "https://www.facebook.com/assets/wearables_dat_display/washer_square.png",
            steps =
                listOf(
                    CarMaintenanceTutorialStep(
                        description =
                            "Open the hood and locate the washer fluid reservoir cap with the windshield symbol.",
                    ),
                    CarMaintenanceTutorialStep(
                        description =
                            "Pour washer fluid into the reservoir carefully until it reaches the fill line.",
                    ),
                    CarMaintenanceTutorialStep(
                        description =
                            "Close the cap securely and test the sprayers to confirm proper flow.",
                    ),
                ),
        ),
    )
  }

  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
  private val sessionLock = Any()

  private val _uiState = MutableStateFlow(DisplayUIState())
  val uiState: StateFlow<DisplayUIState> = _uiState.asStateFlow()

  private val sessionObserverExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e(TAG, "Session state observer failed", throwable)
  }
  private val displayObserverExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e(TAG, "Display state observer failed", throwable)
  }
  private val sendContentExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e(TAG, "Sending display content failed", throwable)
    _uiState.value =
        _uiState.value.copy(
            isSending = false,
            hasSentContent = false,
            errorMessage = throwable.message ?: "Unexpected error",
            snackbarMessage = "Send failed: ${throwable.message ?: "unexpected error"}",
        )
  }

  @GuardedBy("sessionLock") private var session: DeviceSession? = null
  @GuardedBy("sessionLock") private var display: Display? = null
  @GuardedBy("sessionLock") private var sessionStateJob: Job? = null
  @GuardedBy("sessionLock") private var sessionErrorJob: Job? = null
  @GuardedBy("sessionLock") private var displayStateJob: Job? = null
  @GuardedBy("sessionLock") private var pendingDisplayAttachment = false
  @GuardedBy("sessionLock") private var pendingSample: SampleApp? = null
  private var tutorialVideoStateJob: Job? = null

  private val houseHud =
      HouseHudController(
          context = application,
          scope = viewModelScope,
          send = { content -> sendContent(content) },
      )

  val scanState = houseHud.state
  private val vision = GlassesVision(viewModelScope, houseHud::observe, houseHud::cameraAvailability)
  val visionState = vision.state
  private val cameraPermissions = CameraPermissionRequests()
  suspend fun requestCameraPermission(launch: () -> Unit): PermissionStatus = cameraPermissions.request(launch)
  fun completeCameraPermission(status: PermissionStatus) = cameraPermissions.complete(status)

  private var cameraRequested = false
  private var scanStartJob: Job? = null

  fun startScanning(requestPermission: suspend (Permission) -> PermissionStatus) {
    if (scanStartJob?.isActive == true || scanState.value.active) return
    if (androidx.core.content.ContextCompat.checkSelfPermission(getApplication(),
        android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      _uiState.value = _uiState.value.copy(errorMessage = "Precise location permission required. Open App permissions, allow location, then start scanning.")
      return
    }
    scanStartJob = viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isRequestingCamera = true, errorMessage = null)
      try {
        val existing = Wearables.checkPermissionStatus(Permission.CAMERA)
            .getOrDefault(PermissionStatus.Denied)
        val granted = if (existing == PermissionStatus.Granted) existing else requestPermission(Permission.CAMERA)
        cameraRequested = granted == PermissionStatus.Granted
        if (!cameraRequested) vision.unavailable("Camera permission denied. Location estimates remain available.")
        sendSampleToDisplay(SampleApp.BC_HOUSE_HUD)
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (error: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Unable to start scanning")
      } finally { _uiState.value = _uiState.value.copy(isRequestingCamera = false) }
    }
  }

  fun rescanHouse() = houseHud.rescanNow()
  fun selectHouse(site: AddressSite) = houseHud.select(site)

  private var scanWanted = false
  private var recoveryJob: Job? = null
  private var startupWatchdog: Job? = null
  private var recoveryAttempts = 0
  private var lastSelector: DeviceSelector? = null
  private var lastSelectedDeviceId: DeviceIdentifier? = null
  private val contentSender = LatestContentSender<ContentScope.() -> Unit>(viewModelScope) { content ->
    val current = synchronized(sessionLock) { display }
    if (current == null || _uiState.value.displayState != DisplayState.STARTED) false
    else {
      _uiState.update { it.copy(isSending = true) }
      try {
        val result = current.sendContent(content)
        var succeeded = false
        result.fold(onSuccess = {
          if (current === synchronized(sessionLock) { display }) {
            recoveryAttempts = 0
            _uiState.update { it.copy(isSending = false, hasSentContent = true, errorMessage = null) }
            succeeded = true
          }
        }, onFailure = { error, _ ->
          _uiState.update { it.copy(isSending = false, errorMessage = error.description) }
          scheduleRecovery("HUD send failed: ${error.description}")
        })
        succeeded
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (error: Exception) {
        _uiState.update { it.copy(isSending = false, errorMessage = error.message) }
        scheduleRecovery("HUD transport interrupted")
        false
      }
    }
  }

  private fun scheduleRecovery(reason: String) {
    if (!scanWanted || recoveryJob?.isActive == true) return
    contentSender.disconnected()
    if (recoveryAttempts >= 5) {
      viewModelScope.launch {
        stopSession()
        _uiState.update { it.copy(errorMessage = "Glasses could not reconnect. Check the Meta AI connection and start again.") }
      }
      return
    }
    val waitMs = listOf(1000L,2000L,4000L,8000L,15000L)[recoveryAttempts++]
    _uiState.update { it.copy(isPreparingDisplay = true, snackbarMessage = "$reason · reconnecting") }
    recoveryJob = viewModelScope.launch {
      delay(waitMs)
      if (!scanWanted) return@launch
      val selector = lastSelector ?: return@launch
      startupWatchdog?.cancel()
      vision.stop()
      clearSessionStateJob()?.cancel(); clearSessionErrorJob()?.cancel()
      val old = synchronized(sessionLock) { session }
      old?.removeDisplay(); cleanupDisplay()
      clearSession()?.stop()
      _uiState.update { it.copy(isSessionActive = false, isDisplayAttached = false, displayState = null,
          isStartingSession = false, hasSentContent = false) }
      synchronized(sessionLock) {
        pendingDisplayAttachment = true
        pendingSample = if (scanState.value.active) null else SampleApp.BC_HOUSE_HUD
      }
      // Clear the running-job gate before a synchronous create/attach failure schedules another try.
      recoveryJob = null
      startSession(selector, lastSelectedDeviceId)
    }
  }

  // -- Session lifecycle --

  private fun startSession(
      deviceSelector: DeviceSelector,
      selectedDeviceId: DeviceIdentifier?,
  ) {
    lastSelector = deviceSelector
    lastSelectedDeviceId = selectedDeviceId
    startupWatchdog?.cancel()
    startupWatchdog = viewModelScope.launch {
      delay(15000)
      if (scanWanted && (!_uiState.value.isSessionActive || _uiState.value.displayState != DisplayState.STARTED || !_uiState.value.hasSentContent)) {
        scheduleRecovery("Glasses display timed out")
      }
    }
    Log.d(TAG, "Starting DAT session")
    _uiState.value =
        _uiState.value.copy(
            selectedDeviceId = selectedDeviceId,
            connectionState = DwaConnectionState.CONNECTING,
            isStartingSession = true,
            isPreparingDisplay = true,
            hasSentContent = false,
            errorMessage = null,
            snackbarMessage = "Starting session...",
        )

    val result = Wearables.createSession(deviceSelector)
    result.fold(
        onSuccess = { newSession ->
          synchronized(sessionLock) { session = newSession }

          val stateJob =
              viewModelScope.launch(sessionObserverExceptionHandler) {
                var hasStarted = false
                newSession.state.collect { state ->
                  if (newSession !== synchronized(sessionLock) { session }) return@collect
                  when (state) {
                    DeviceSessionState.STARTED -> {
                      hasStarted = true
                      Log.i(TAG, "Session started")
                      _uiState.value =
                          _uiState.value.copy(
                              selectedDeviceId = selectedDeviceId,
                              connectionState = DwaConnectionState.CONNECTED,
                              isSessionActive = true,
                              isStartingSession = false,
                              isDatAppUpdateRequired = false,
                              snackbarMessage = "Session started",
                          )
                      if (consumePendingDisplayAttachment()) {
                        attachDisplay()
                      }
                    }
                    DeviceSessionState.STOPPED -> {
                      Log.i(TAG, "Session stopped")
                      if (!hasStarted && _uiState.value.isStartingSession) return@collect
                      vision.stop()
                      contentSender.disconnected()
                      if (!scanWanted) houseHud.stop()
                      clearPendingWork()
                      cleanupDisplay()
                      _uiState.value =
                          _uiState.value.copy(
                              connectionState = DwaConnectionState.DISCONNECTED,
                              isSessionActive = false,
                              isStartingSession = false,
                              isStoppingSession = false,
                              isDisplayAttached = false,
                              isPreparingDisplay = false,
                              isSending = false,
                              hasSentContent = false,
                              selectedDeviceId = null,
                              displayState = null,
                              snackbarMessage = "Session stopped",
                          )
                      scheduleRecovery("Glasses disconnected")
                    }
                    else -> {}
                  }
                }
              }
          replaceSessionStateJob(stateJob)

          val errorJob =
              viewModelScope.launch(sessionObserverExceptionHandler) {
                newSession.errors.collect { error ->
                  if (newSession === synchronized(sessionLock) { session }) handleSessionError(error)
                }
              }
          replaceSessionErrorJob(errorJob)

          newSession.start()
        },
        onFailure = { error, _ ->
          Log.e(TAG, "Failed to create session: ${error.description}")
          clearPendingWork()
          _uiState.value =
              _uiState.value.copy(
                  connectionState = DwaConnectionState.DISCONNECTED,
                  isStartingSession = false,
                  isPreparingDisplay = false,
                  isDatAppUpdateRequired =
                      error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED,
                  isSending = false,
                  hasSentContent = false,
                  selectedDeviceId = null,
                  errorMessage = error.description,
                  snackbarMessage = "Failed: ${error.description}",
              )
          if (error != DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) scheduleRecovery(error.description)
        },
    )
  }

  private fun prepareDisplayConnection(
      deviceSelector: DeviceSelector,
      selectedDeviceId: DeviceIdentifier?,
  ) {
    when {
      _uiState.value.isDisplayAttached -> {
        synchronized(sessionLock) { pendingDisplayAttachment = false }
        _uiState.value =
            _uiState.value.copy(
                selectedDeviceId = selectedDeviceId,
                isPreparingDisplay = _uiState.value.displayState != DisplayState.STARTED,
            )
        if (_uiState.value.displayState == DisplayState.STARTED) sendPendingSample()
      }
      _uiState.value.isSessionActive -> {
        synchronized(sessionLock) { pendingDisplayAttachment = false }
        _uiState.value =
            _uiState.value.copy(
                selectedDeviceId = selectedDeviceId,
                isPreparingDisplay = true,
                snackbarMessage = "Attaching display...",
            )
        attachDisplay()
      }
      else -> {
        synchronized(sessionLock) { pendingDisplayAttachment = true }
        startSession(deviceSelector, selectedDeviceId)
      }
    }
  }

  private fun attachDisplay() {
    val currentSession =
        synchronized(sessionLock) { session }
            ?: run {
              clearPendingWork()
              _uiState.value =
                  _uiState.value.copy(
                      isPreparingDisplay = false,
                      isSending = false,
                      hasSentContent = false,
                      errorMessage = "No active session",
                      snackbarMessage = "No active session",
                  )
              return
            }

    Log.d(TAG, "Attaching display")
    _uiState.value = _uiState.value.copy(snackbarMessage = "Attaching display...")

    currentSession
        .addDisplay()
        .fold(
            onSuccess = { newDisplay ->
              synchronized(sessionLock) { display = newDisplay }
              Log.i(TAG, "Display attached")
              _uiState.value =
                  _uiState.value.copy(
                      isDisplayAttached = true,
                      isPreparingDisplay = true,
                      snackbarMessage = "Display attached",
                  )

              val stateJob =
                  viewModelScope.launch(displayObserverExceptionHandler) {
                    var hasStarted = false
                    newDisplay.state.collect { state ->
                      if (newDisplay !== synchronized(sessionLock) { display }) return@collect
                      Log.i(TAG, "Display state: $state")
                      _uiState.value =
                          _uiState.value.copy(
                              displayState = state,
                              isPreparingDisplay =
                                  state != DisplayState.STARTED && state != DisplayState.STOPPED,
                          )
                      if (state == DisplayState.STARTED) {
                        hasStarted = true
                        _uiState.value =
                            _uiState.value.copy(
                                isPreparingDisplay = false,
                                snackbarMessage = "Display ready",
                            )
                        contentSender.ready()
                        sendPendingSample()
                        if (scanState.value.active && cameraRequested && !vision.isAttached) {
                          synchronized(sessionLock) { session }?.let(vision::start)
                        }
                      }
                      if (state == DisplayState.STOPPED && hasStarted) {
                        _uiState.value =
                            _uiState.value.copy(
                                isPreparingDisplay = false,
                                snackbarMessage = "Display session stopped",
                            )
                        contentSender.disconnected()
                        scheduleRecovery("Glasses display stopped")
                      }
                    }
                  }
              replaceDisplayStateJob(stateJob)
            },
            onFailure = { error, _ ->
              Log.e(TAG, "Failed to attach display: ${error.description}")
              clearPendingWork()
              _uiState.value =
                  _uiState.value.copy(
                      isPreparingDisplay = false,
                      isSending = false,
                      hasSentContent = false,
                      errorMessage = error.description,
                      snackbarMessage = "Failed: ${error.description}",
                  )
              scheduleRecovery("Could not attach glasses display")
            },
        )
  }

  // -- Content --

  private fun sendContent(content: ContentScope.() -> Unit) {
    contentSender.submit(content)
  }

  // -- Sample management --

  fun sendSampleToDisplay(sample: SampleApp) {
    queueOrSendSample(
        sample = sample,
        deviceSelector = AutoDeviceSelector(filter = { it.isDisplayCapable() && it.linkState == com.meta.wearable.dat.core.types.LinkState.CONNECTED }),
        selectedDeviceId = null,
    )
  }

  fun sendSampleToPreview(
      sample: SampleApp,
      deviceId: DeviceIdentifier,
  ) {
    cameraRequested = false
    vision.unavailable("Phone display preview · no glasses camera")
    queueOrSendSample(
        sample = sample,
        deviceSelector = SpecificDeviceSelector(deviceId),
        selectedDeviceId = deviceId,
    )
  }

  private fun queueOrSendSample(
      sample: SampleApp,
      deviceSelector: DeviceSelector,
      selectedDeviceId: DeviceIdentifier?,
  ) {
    if (sample == SampleApp.BC_HOUSE_HUD) {
      scanWanted = true
      if (androidx.core.content.ContextCompat.checkSelfPermission(getApplication(),
          android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        ScanService.start(getApplication()) { viewModelScope.launch { stopSession() } }
      }
    }
    if (_uiState.value.displayState == DisplayState.STARTED) {
      sendSampleContent(sample)
      return
    }

    synchronized(sessionLock) { pendingSample = sample }
    _uiState.value =
        _uiState.value.copy(
            hasSentContent = false,
            errorMessage = null,
        )
    prepareDisplayConnection(deviceSelector, selectedDeviceId)
  }

  private fun sendPendingSample() {
    val sample =
        synchronized(sessionLock) {
          val pending = pendingSample
          pendingSample = null
          pending
        }
    if (sample != null) sendSampleContent(sample)
  }

  private fun sendSampleContent(sample: SampleApp) {
    Log.i(TAG, "Sending sample to display: ${sample.name}")
    when (sample) {
      SampleApp.BC_HOUSE_HUD -> {
        if (!scanState.value.active) houseHud.start()
        if (cameraRequested && scanState.value.active && !vision.isAttached) {
          synchronized(sessionLock) { session }?.let(vision::start)
        }
      }
      SampleApp.CAR_MAINTENANCE -> displayCarMaintenanceScreen()
    }
  }

  // -- Cleanup --

  fun clearSnackbarMessage() {
    _uiState.value = _uiState.value.copy(snackbarMessage = null)
  }

  private fun detachDisplay() {
    Log.d(TAG, "Detaching display")
    synchronized(sessionLock) { session }?.removeDisplay()
    cleanupDisplay()
    _uiState.value =
        _uiState.value.copy(
            isDisplayAttached = false,
            isPreparingDisplay = false,
            isSending = false,
            hasSentContent = false,
            snackbarMessage = "Display detached",
        )
  }

  suspend fun stopSession(): Boolean {
    return withContext(Dispatchers.Main.immediate) { stopSessionImmediately() }
  }

  private fun stopSessionImmediately(): Boolean {
    return try {
      Log.d(TAG, "Stopping session")
      scanWanted = false
      recoveryJob?.cancel(); recoveryJob = null
      startupWatchdog?.cancel(); startupWatchdog = null
      scanStartJob?.cancel()
      contentSender.clear()
      ScanService.stop(getApplication())
      vision.stop()
      houseHud.stop()
      cameraRequested = false
      clearPendingWork()
      _uiState.value =
          _uiState.value.copy(
              isStoppingSession = true,
              isPreparingDisplay = false,
              isSending = false,
              hasSentContent = false,
              errorMessage = null,
              snackbarMessage = "Stopping session...",
          )

      detachDisplay()
      clearSessionStateJob()?.cancel()
      clearSessionErrorJob()?.cancel()
      clearSession()?.stop()

      _uiState.value =
          _uiState.value.copy(
              connectionState = DwaConnectionState.DISCONNECTED,
              isSessionActive = false,
              isStartingSession = false,
              isStoppingSession = false,
              isDisplayAttached = false,
              selectedDeviceId = null,
              displayState = null,
              snackbarMessage = "Session stopped",
          )
      true
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      Log.e(TAG, "Stopping session failed", error)
      val message = getApplication<Application>().getString(R.string.stop_session_error)
      _uiState.value =
          _uiState.value.copy(
              isStoppingSession = false,
              isPreparingDisplay = false,
              isSending = false,
              hasSentContent = false,
              errorMessage = message,
              snackbarMessage = message,
          )
      false
    }
  }

  private fun cleanupDisplay() {
    tutorialVideoStateJob?.cancel()
    tutorialVideoStateJob = null
    clearDisplayStateJob()?.cancel()
    synchronized(sessionLock) { display = null }
  }

  private fun handleSessionError(error: DeviceSessionError) {
    Log.e(TAG, "Session error: ${error.description}")
    vision.stop()
    contentSender.disconnected()
    if (!scanWanted) houseHud.stop()
    clearPendingWork()
    cleanupDisplay()
    clearSessionStateJob()?.cancel()
    clearSessionErrorJob()?.cancel()
    clearSession()?.stop()
    _uiState.value =
        _uiState.value.copy(
            connectionState = DwaConnectionState.DISCONNECTED,
            isSessionActive = false,
            isStartingSession = false,
            isPreparingDisplay = false,
            isDisplayAttached = false,
            isDatAppUpdateRequired =
                error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED,
            isSending = false,
            hasSentContent = false,
            selectedDeviceId = null,
            displayState = null,
            errorMessage = error.description,
            snackbarMessage = error.description,
        )
    if (error != DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) scheduleRecovery(error.description)
  }

  private fun displayCarMaintenanceScreen() {
    sendContent { carMaintenanceTutorialListContent() }
  }

  private fun displayCarMaintenanceTutorialDetail(tutorial: CarMaintenanceTutorial) {
    sendContent {
      flexBox(direction = Direction.COLUMN, gap = 12) {
        flexBox(padding = 24, background = FlexBoxBackground.CARD) {
          tutorialImage(tutorial.imageRes, tutorial.imageUri)
          text(tutorial.title, style = TextStyle.HEADING)
          text(tutorial.duration, style = TextStyle.META, color = TextColor.SECONDARY)
        }
        flexBox(
            direction = Direction.ROW,
            gap = 8,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
            wrap = true,
        ) {
          buttonGroup {
            button(
                "Back",
                onClick = { displayCarMaintenanceScreen() },
            )
            button(
                "Start",
                onClick = { displayCarMaintenanceTutorialStep(tutorial, 0) },
            )
          }
        }
      }
    }
  }

  private fun displayTutorialVideo(
      tutorial: CarMaintenanceTutorial,
      stepIndex: Int,
  ) {
    viewModelScope.launch(dispatcher + sendContentExceptionHandler) {
      val currentDisplay =
          synchronized(sessionLock) { display }
              ?: run {
                _uiState.value = _uiState.value.copy(snackbarMessage = "No display attached")
                return@launch
              }

      val player = VideoPlayer(
          source = VideoSource.Url(TUTORIAL_VIDEO_URL),
          codec = VideoCodec.MP4,
      )

      val result = currentDisplay.sendContent { video(player = player) }
      result.fold(
          onSuccess = {
            tutorialVideoStateJob?.cancel()
            tutorialVideoStateJob = viewModelScope.launch {
              player.state.collect { state ->
                if (state == VideoPlayerState.ENDED) {
                  tutorialVideoStateJob?.cancel()
                  tutorialVideoStateJob = null
                  displayCarMaintenanceTutorialStep(tutorial, stepIndex)
                }
              }
            }
            _uiState.value = _uiState.value.copy(snackbarMessage = "Starting video...")
            player.play()
          },
          onFailure = { error, _ ->
            _uiState.value =
                _uiState.value.copy(snackbarMessage = "Send failed: ${error.description}")
          },
      )
    }
  }

  private fun displayCarMaintenanceTutorialStep(
      tutorial: CarMaintenanceTutorial,
      stepIndex: Int,
  ) {
    val clampedIndex = stepIndex.coerceIn(0, tutorial.steps.lastIndex)
    val step = tutorial.steps[clampedIndex]
    sendContent {
      flexBox(direction = Direction.COLUMN, gap = 12) {
        flexBox(padding = 24, background = FlexBoxBackground.CARD) {
          text(
              "Step ${clampedIndex + 1}",
              style = TextStyle.META,
              color = TextColor.SECONDARY,
          )
          text(step.description, style = TextStyle.BODY)
        }

        flexBox(
            direction = Direction.ROW,
            gap = 8,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
        ) {
          val isLastStep = clampedIndex == tutorial.steps.lastIndex
          buttonGroup {
            button(
                "Previous",
                style = ButtonStyle.PRIMARY,
                iconName = IconName.TRIANGLE_LEFT_VERTICAL_LINE,
                onClick = {
                  if (clampedIndex == 0) {
                    displayCarMaintenanceTutorialDetail(tutorial)
                  } else {
                    displayCarMaintenanceTutorialStep(tutorial, clampedIndex - 1)
                  }
                },
            )
            button(
                if (isLastStep) "Done" else "Next",
                style = ButtonStyle.PRIMARY,
                iconName =
                    if (isLastStep) {
                      IconName.CHECKMARK
                    } else {
                      IconName.TRIANGLE_RIGHT_VERTICAL_LINE
                    },
                onClick = {
                  if (isLastStep) {
                    displayCarMaintenanceScreen()
                  } else if (clampedIndex < tutorial.steps.lastIndex) {
                    displayCarMaintenanceTutorialStep(tutorial, clampedIndex + 1)
                  }
                },
                actionRole = ActionRole.PRIMARY,
            )
            button(
                "Watch video",
                style = ButtonStyle.SECONDARY,
                iconName = IconName.VIDEO_CAMERA,
                onClick = { displayTutorialVideo(tutorial, clampedIndex) },
            )
          }
        }
      }
    }
  }

  private fun ContentScope.carMaintenanceTutorialListContent() {
    flexBox(direction = Direction.COLUMN, gap = 10) {
      carMaintenanceTutorials.forEach { tutorial -> maintenanceTutorialItem(tutorial) }
    }
  }

  private fun FlexBoxScope.maintenanceTutorialItem(tutorial: CarMaintenanceTutorial) {
    flexBox(
        padding = 24,
        background = FlexBoxBackground.CARD,
        onClick = { displayCarMaintenanceTutorialDetail(tutorial) },
    ) {
      flexBox(direction = Direction.ROW, gap = 12, crossAlignment = Alignment.CENTER) {
        if (tutorial.iconRes != null || tutorial.iconImageUri != null) {
          flexBox(direction = Direction.COLUMN, flexGrow = 1f) {
            tutorialImage(tutorial.iconRes, tutorial.iconImageUri)
          }
        }
        flexBox(direction = Direction.COLUMN, flexGrow = 7f) {
          text(tutorial.title, style = TextStyle.BODY)
          text(tutorial.duration, style = TextStyle.META, color = TextColor.SECONDARY)
        }
      }
    }
  }

  private fun FlexBoxScope.tutorialImage(@DrawableRes res: Int?, uri: String?) {
    val bitmap = res?.let { resId ->
      BitmapFactory.decodeResource(getApplication<Application>().resources, resId)
    }
    when {
      bitmap != null ->
          image(bitmap = bitmap, sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
      uri != null ->
          image(uri = uri, sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
    }
  }

  override fun onCleared() {
    stopSessionImmediately()
    contentSender.close()
    super.onCleared()
  }

  private fun consumePendingDisplayAttachment(): Boolean =
      synchronized(sessionLock) {
        val shouldAttach = pendingDisplayAttachment && !_uiState.value.isDisplayAttached
        pendingDisplayAttachment = false
        shouldAttach
      }

  private fun clearPendingWork() {
    synchronized(sessionLock) {
      pendingDisplayAttachment = false
      pendingSample = null
    }
  }

  private fun replaceSessionStateJob(job: Job) {
    synchronized(sessionLock) {
      sessionStateJob?.cancel()
      sessionStateJob = job
    }
  }

  private fun replaceSessionErrorJob(job: Job) {
    synchronized(sessionLock) {
      sessionErrorJob?.cancel()
      sessionErrorJob = job
    }
  }

  private fun replaceDisplayStateJob(job: Job) {
    synchronized(sessionLock) {
      displayStateJob?.cancel()
      displayStateJob = job
    }
  }

  private fun clearSessionStateJob(): Job? =
      synchronized(sessionLock) {
        val currentJob = sessionStateJob
        sessionStateJob = null
        currentJob
      }

  private fun clearSessionErrorJob(): Job? =
      synchronized(sessionLock) {
        val currentJob = sessionErrorJob
        sessionErrorJob = null
        currentJob
      }

  private fun clearDisplayStateJob(): Job? =
      synchronized(sessionLock) {
        val currentJob = displayStateJob
        displayStateJob = null
        currentJob
      }

  private fun clearSession(): DeviceSession? =
      synchronized(sessionLock) {
        val currentSession = session
        session = null
        currentSession
      }
}

private data class CarMaintenanceTutorial(
    val title: String,
    val duration: String,
    val imageUri: String? = null,
    val iconImageUri: String? = null,
    @param:DrawableRes val imageRes: Int? = null,
    @param:DrawableRes val iconRes: Int? = null,
    val steps: List<CarMaintenanceTutorialStep>,
)

private data class CarMaintenanceTutorialStep(
    val description: String,
)
