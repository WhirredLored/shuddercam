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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.huestone.shuddercam.brand.Brand
import com.huestone.shuddercam.camera.CameraCaps
import com.huestone.shuddercam.camera.CameraController
import com.huestone.shuddercam.controls.ControlId
import com.huestone.shuddercam.controls.ControlSpec
import com.huestone.shuddercam.controls.ControlValue
import com.huestone.shuddercam.controls.EffectiveSettings
import com.huestone.shuddercam.controls.Presets
import com.huestone.shuddercam.data.Prefs
import kotlinx.coroutines.launch

@Composable
fun ViewfinderScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var permissionGranted by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var hasRequestedOnce by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var isFocusTouching by remember { mutableStateOf(false) }
    var allCaps by remember { mutableStateOf<List<CameraCaps>>(emptyList()) }
    var specs by remember { mutableStateOf<Map<ControlId, ControlSpec>>(emptyMap()) }
    var resolved by remember { mutableStateOf<Map<ControlId, EffectiveSettings.Resolution>>(emptyMap()) }

    val prefs = remember { Prefs(context) }
    val overrides by prefs.overrides.collectAsState(initial = emptyMap())

    // Check if in Manual Focus mode (AF_MODE == 0)
    val afRes = resolved[ControlId.AF_MODE] as? EffectiveSettings.Resolution.Resolved
    val isMf = (afRes?.value as? ControlValue.IntValue)?.value == 0

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

    if (showDiagnostics) {
        DiagnosticsScreen(
            allCaps = allCaps,
            onBack = { showDiagnostics = false }
        )
        return
    }

    if (!permissionGranted) {
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
        val cameraController = remember { CameraController(context) }

        // Resolve and apply whenever overrides or specs change
        LaunchedEffect(overrides, specs) {
            if (specs.isNotEmpty()) {
                val newResolved = EffectiveSettings.resolveAll(
                    specs = specs,
                    activePreset = Presets.DEVICE_AUTO,
                    overrides = overrides
                )
                resolved = newResolved
                cameraController.applySettings(newResolved)
            }
        }

        DisposableEffect(lifecycleOwner) {
            onDispose {
                cameraController.unbind()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        previewView.implementationMode =
                            PreviewView.ImplementationMode.COMPATIBLE
                        cameraController.bindToLifecycle(
                            lifecycleOwner,
                            previewView
                        ) { caps ->
                            allCaps = caps
                            specs = cameraController.specs
                        }

                        previewView.setOnTouchListener { view, event ->
                            if (event.action == MotionEvent.ACTION_DOWN) {
                                // Tap-to-focus fires only when NOT in manual focus mode
                                if (!isMf) {
                                    val factory = previewView.meteringPointFactory
                                    cameraController.tapToFocus(factory, event.x, event.y)
                                    view.performClick()
                                }
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            )

            // Center MF Loupe Assist Overlay when adjusting manual focus slider
            if (isFocusTouching) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(70.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "FOCUS LOUPE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val fdRes = resolved[ControlId.FOCUS_DISTANCE] as? EffectiveSettings.Resolution.Resolved
                        val fdVal = (fdRes?.value as? ControlValue.FloatValue)?.value ?: 0f
                        Text(
                            text = formatDioptersAsMeters(fdVal),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Top bar: exposure readout chip + info/controls icons
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exposure readout chip
                ExposureReadout(resolved)

                Spacer(modifier = Modifier.weight(1f))

                // Controls toggle
                IconButton(onClick = { showControls = !showControls }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Controls",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Info icon
                IconButton(onClick = { showDiagnostics = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Camera Diagnostics",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bottom controls sheet
            if (showControls && specs.isNotEmpty()) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    ControlsSheet(
                        specs = specs,
                        resolved = resolved,
                        onOverride = { id, value ->
                            scope.launch {
                                prefs.setOverride(id, value)
                            }
                        },
                        onFocusTouching = { isFocusTouching = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExposureReadout(resolved: Map<ControlId, EffectiveSettings.Resolution>) {
    val aeRes = resolved[ControlId.AE_MODE] as? EffectiveSettings.Resolution.Resolved
    val aeMode = (aeRes?.value as? ControlValue.IntValue)?.value ?: 1
    val isManual = aeMode == 0

    val displayParts = mutableListOf<String>()

    if (isManual) {
        val isoRes = resolved[ControlId.ISO] as? EffectiveSettings.Resolution.Resolved
        val iso = (isoRes?.value as? ControlValue.IntValue)?.value
        if (iso != null && iso > 0) displayParts.add("ISO $iso")

        val shutterRes = resolved[ControlId.SHUTTER_NS] as? EffectiveSettings.Resolution.Resolved
        val ns = (shutterRes?.value as? ControlValue.LongValue)?.value
        if (ns != null && ns > 0) displayParts.add(formatShutterSpeed(ns))
    } else {
        displayParts.add("Auto")
        val evRes = resolved[ControlId.AE_COMPENSATION] as? EffectiveSettings.Resolution.Resolved
        val ev = (evRes?.value as? ControlValue.IntValue)?.value
        if (ev != null && ev != 0) {
            displayParts.add(if (ev > 0) "EV+$ev" else "EV$ev")
        }
    }

    // WB readout
    val awbRes = resolved[ControlId.AWB_MODE] as? EffectiveSettings.Resolution.Resolved
    val awbMode = (awbRes?.value as? ControlValue.IntValue)?.value ?: 1
    if (awbMode == 0) {
        val tempRes = resolved[ControlId.COLOR_TEMP_K] as? EffectiveSettings.Resolution.Resolved
        val kelvin = (tempRes?.value as? ControlValue.IntValue)?.value ?: 5500
        displayParts.add("${kelvin}K")
    } else if (awbMode != 1) {
        val modeName = when (awbMode) {
            2 -> "Tungsten"
            5 -> "Daylight"
            6 -> "Cloudy"
            else -> "WB $awbMode"
        }
        displayParts.add(modeName)
    }

    if (displayParts.isNotEmpty()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            displayParts.forEachIndexed { index, part ->
                if (index > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = part,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
