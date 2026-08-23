package com.huestone.shuddercam.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaActionSound
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.huestone.shuddercam.brand.Brand
import com.huestone.shuddercam.camera.CameraCaps
import com.huestone.shuddercam.camera.CameraController
import com.huestone.shuddercam.camera.CaptureController
import com.huestone.shuddercam.controls.ControlId
import com.huestone.shuddercam.controls.ControlSpec
import com.huestone.shuddercam.controls.ControlValue
import com.huestone.shuddercam.controls.EffectiveSettings
import com.huestone.shuddercam.controls.Presets
import com.huestone.shuddercam.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isAbHolding by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var isBlinking by remember { mutableStateOf(false) }
    var lastShotUri by remember { mutableStateOf<Uri?>(null) }
    var lastShotThumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var allCaps by remember { mutableStateOf<List<CameraCaps>>(emptyList()) }
    var specs by remember { mutableStateOf<Map<ControlId, ControlSpec>>(emptyMap()) }
    var resolved by remember { mutableStateOf<Map<ControlId, EffectiveSettings.Resolution>>(emptyMap()) }

    val prefs = remember { Prefs(context) }
    val overrides by prefs.overrides.collectAsState(initial = emptyMap())
    val activePresetId by prefs.activePresetId.collectAsState(initial = Presets.DEVICE_AUTO.id)
    val activePreset = remember(activePresetId) { Presets.byId(activePresetId) }

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

    // Pre-Q writes go through a file path, which needs the storage permission.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Denial surfaces as a capture error rather than blocking the viewfinder. */ }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
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

        // Resolve and apply whenever overrides, specs, or the A/B hold change.
        // Holding suspends overrides without clearing them, so releasing restores
        // the tweaked look immediately.
        LaunchedEffect(overrides, specs, isAbHolding, activePreset) {
            if (specs.isNotEmpty()) {
                val newResolved = EffectiveSettings.resolveAll(
                    specs = specs,
                    activePreset = activePreset,
                    overrides = if (isAbHolding) emptyMap() else overrides
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

        // Shutter sound honors the system profile: silent and vibrate stay quiet,
        // except where the platform enforces a shutter tone regardless.
        val shutterSound = remember { MediaActionSound() }
        DisposableEffect(Unit) {
            shutterSound.load(MediaActionSound.SHUTTER_CLICK)
            onDispose { shutterSound.release() }
        }

        // Keep EXIF orientation correct as the device rotates.
        val configuration = LocalConfiguration.current
        LaunchedEffect(configuration) {
            previewViewRef?.display?.let { cameraController.setTargetRotation(it.rotation) }
        }

        LaunchedEffect(isBlinking) {
            if (isBlinking) {
                delay(90)
                isBlinking = false
            }
        }

        val onShutter = {
            val capture = cameraController.imageCapture
            if (capture != null && !isCapturing) {
                isCapturing = true
                captureError = null
                isBlinking = true
                if (shouldPlayShutterSound(context)) {
                    shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                }
                CaptureController.capture(context, capture) { result ->
                    isCapturing = false
                    when (result) {
                        is CaptureController.Result.Saved -> {
                            lastShotUri = result.uri
                            scope.launch {
                                lastShotThumb = loadThumbnail(context, result.uri)
                            }
                        }
                        is CaptureController.Result.Failed -> {
                            captureError = result.message
                        }
                    }
                }
            }
        }

        val zoomSpec = specs[ControlId.ZOOM_RATIO]
        val zoomRange = zoomSpec?.floatRange ?: 1.0f..1.0f
        val zoomEnabled = zoomSpec != null && zoomSpec.available &&
            zoomRange.endInclusive > zoomRange.start

        // Gesture handlers outlive the composition that created them, so they read
        // the latest values rather than the ones captured when they were installed.
        val currentResolved by rememberUpdatedState(resolved)
        val currentIsMf by rememberUpdatedState(isMf)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoomEnabled, zoomRange) {
                    if (!zoomEnabled) return@pointerInput
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1.0f) {
                            val zoomRes = currentResolved[ControlId.ZOOM_RATIO]
                                as? EffectiveSettings.Resolution.Resolved
                            val currentZoom =
                                (zoomRes?.value as? ControlValue.FloatValue)?.value ?: 1.0f
                            val newZoom = (currentZoom * zoomChange).coerceIn(zoomRange)
                            // Same override path the sheet's zoom slider writes to.
                            scope.launch {
                                prefs.setOverride(
                                    ControlId.ZOOM_RATIO,
                                    ControlValue.FloatValue(newZoom)
                                )
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Tap-to-focus fires only when NOT in manual focus mode
                        if (!currentIsMf) {
                            previewViewRef?.let { pv ->
                                cameraController.tapToFocus(
                                    pv.meteringPointFactory,
                                    offset.x,
                                    offset.y
                                )
                            }
                        }
                    }
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        previewView.implementationMode =
                            PreviewView.ImplementationMode.COMPATIBLE
                        previewViewRef = previewView
                        cameraController.bindToLifecycle(
                            lifecycleOwner,
                            previewView
                        ) { caps ->
                            allCaps = caps
                            specs = cameraController.specs
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

            // Capture blink — brief dark veil over the preview, so the frame
            // being taken is obvious without blinding a night-adapted eye.
            if (isBlinking) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }

            // Bottom stack: controls sheet (when open) above the always-present
            // shutter bar, so capture works in every mode.
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                if (showControls && specs.isNotEmpty()) {
                    ControlsSheet(
                        specs = specs,
                        resolved = resolved,
                        overriddenIds = overrides.filterValues { it != null }.keys,
                        activePreset = activePreset,
                        isAbHolding = isAbHolding,
                        onOverride = { id, value ->
                            scope.launch {
                                prefs.setOverride(id, value)
                            }
                        },
                        // Overrides survive a preset switch — they shadow it.
                        onSelectPreset = { preset ->
                            scope.launch { prefs.setActivePresetId(preset.id) }
                        },
                        onResetAll = { scope.launch { prefs.clearAllOverrides() } },
                        onAbHoldChange = { isAbHolding = it },
                        onFocusTouching = { isFocusTouching = it }
                    )
                }

                CaptureBar(
                    thumbnail = lastShotThumb,
                    isCapturing = isCapturing,
                    errorMessage = captureError,
                    onShutter = onShutter,
                    onThumbnailClick = {
                        lastShotUri?.let { uri -> openInSystemViewer(context, uri) }
                    }
                )
            }
        }
    }
}

/**
 * Bottom capture bar: last-shot thumbnail on the left, shutter in the centre.
 * There is no in-app gallery — the thumbnail hands off to the system viewer.
 */
@Composable
private fun CaptureBar(
    thumbnail: ImageBitmap?,
    isCapturing: Boolean,
    errorMessage: String?,
    onShutter: () -> Unit,
    onThumbnailClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            // Last shot thumbnail
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .then(
                        if (thumbnail != null) Modifier.clickable { onThumbnailClick() }
                        else Modifier
                    )
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = "Last shot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Shutter button
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCapturing) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    .border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    .clickable(enabled = !isCapturing) { onShutter() }
            )
        }
    }
}

/**
 * True when the system profile allows a shutter tone. Devices whose region
 * mandates the sound play it regardless — MediaActionSound owns that policy.
 */
private fun shouldPlayShutterSound(context: Context): Boolean {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
}

/**
 * Decodes a downscaled thumbnail of a captured image off the main thread.
 */
private suspend fun loadThumbnail(context: Context, uri: Uri): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inSampleSize = 8 }
                BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
            }
        }.getOrNull()
    }

/**
 * Hands the captured image to whatever viewer the OS provides.
 */
private fun openInSystemViewer(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
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

    // Zoom readout
    val zoomRes = resolved[ControlId.ZOOM_RATIO] as? EffectiveSettings.Resolution.Resolved
    val currentZoom = (zoomRes?.value as? ControlValue.FloatValue)?.value ?: 1.0f
    if (currentZoom > 1.05f) {
        displayParts.add(String.format("%.1fx", currentZoom))
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
