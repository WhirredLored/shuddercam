package com.huestone.shuddercam.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.huestone.shuddercam.brand.Brand
import com.huestone.shuddercam.camera.CameraController

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ViewfinderScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionGranted by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var hasRequestedOnce by remember { mutableStateOf(false) }

    // Check initial permission state
    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
        if (!isGranted && hasRequestedOnce) {
            permanentlyDenied = true
        }
        hasRequestedOnce = true
    }

    if (!permissionGranted) {
        // Permission request / rationale / settings deep-link UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Brand.APP_NAME,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (permanentlyDenied) {
                    Text(
                        text = "Camera permission is required.\nPlease grant it in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Open Settings")
                    }
                } else {
                    Text(
                        text = "Camera access is needed to show the viewfinder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Grant Camera Permission")
                    }
                }
            }
        }
    } else {
        // Camera preview
        val cameraController = remember { CameraController(context) }

        DisposableEffect(lifecycleOwner) {
            onDispose {
                cameraController.unbind()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            // Tap-to-focus will be wired to the PreviewView's metering factory
                            true
                        } else {
                            false
                        }
                    },
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        previewView.implementationMode =
                            PreviewView.ImplementationMode.COMPATIBLE
                        cameraController.bindToLifecycle(lifecycleOwner, previewView)

                        previewView.setOnTouchListener { view, event ->
                            if (event.action == MotionEvent.ACTION_DOWN) {
                                val factory = previewView.meteringPointFactory
                                cameraController.tapToFocus(factory, event.x, event.y)
                                view.performClick()
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            )
        }
    }
}
