package com.meta.wearable.dat.externalsampleapps.displayaccess

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.displayaccess.ui.AppScaffold
import com.meta.wearable.dat.externalsampleapps.displayaccess.ui.DisplayAccessTheme
import com.meta.wearable.dat.externalsampleapps.displayaccess.display.DisplayViewModel
import com.meta.wearable.dat.externalsampleapps.displayaccess.wearables.WearablesViewModel
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
  private val wearablesViewModel: WearablesViewModel by viewModels()
  private val displayViewModel: DisplayViewModel by viewModels()
  private var initialized = false
  private val requiredPermissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT,
      Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA)
  private val permissionsLauncher = registerForActivityResult(RequestMultiplePermissions()) {
    initializeWearables()
  }
  private val permissionMutex = Mutex()
  private var permissionContinuation: CancellableContinuation<PermissionStatus>? = null
  private val wearablesPermissionLauncher = registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
    permissionContinuation?.resume(result.getOrDefault(PermissionStatus.Denied))
    permissionContinuation = null
  }

  private fun initializeWearables() {
    if (initialized) return
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      Toast.makeText(this, "Allow nearby devices in Android settings to connect glasses", Toast.LENGTH_LONG).show()
      return
    }
    Wearables.initialize(this)
    initialized = true
    wearablesViewModel.startObserving()
  }

  private suspend fun requestWearablesPermission(permission: Permission): PermissionStatus = permissionMutex.withLock {
    suspendCancellableCoroutine { continuation ->
      permissionContinuation = continuation
      continuation.invokeOnCancellation { permissionContinuation = null }
      wearablesPermissionLauncher.launch(permission)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { DisplayAccessTheme { AppScaffold(wearablesViewModel = wearablesViewModel,
        onRequestCameraPermission = ::requestWearablesPermission) } }
    if (requiredPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
      initializeWearables()
    } else permissionsLauncher.launch(requiredPermissions)
  }

  override fun onStop() {
    super.onStop()
    displayViewModel.pauseScanning()
  }
}
