package com.huestone.shuddercam.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaActionSound
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.huestone.shuddercam.brand.Brand
import com.huestone.shuddercam.camera.CameraCaps
import com.huestone.shuddercam.camera.CameraController
import com.huestone.shuddercam.camera.CaptureController
import com.huestone.shuddercam.controls.ControlId
import com.huestone.shuddercam.controls.ControlSpec
import com.huestone.shuddercam.controls.ControlValue
import com.huestone.shuddercam.controls.EffectiveSettings
import com.huestone.shuddercam.controls.Preset
import com.huestone.shuddercam.controls.PresetCodec
import com.huestone.shuddercam.controls.Presets
import com.huestone.shuddercam.controls.ThermalTier
import com.huestone.shuddercam.data.Prefs
import com.huestone.shuddercam.util.EdgelessController
import com.huestone.shuddercam.util.ScreenBrightness
import com.huestone.shuddercam.util.ThermalGovernor
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ALLOCATION AUDIT (C16) — what was checked along the preview path and the loupe,
 * by reading every allocation site on the way to a frame:
 *
 * - No per-frame app code exists at all: only Preview and ImageCapture are bound,
 *   with no ImageAnalysis use case and no frame callback, so nothing of ours runs
 *   between frames. PreviewView itself is built once inside the AndroidView
 *   factory, never on recomposition.
 * - The loupe allocates only while a finger is on the focus slider — it reads the
 *   resolved value and formats one string per drag event, not per frame — and it
 *   stands down entirely above ThermalTier.NORMAL.
 * - Thermal and battery are event-driven (a PowerManager listener and the sticky
 *   battery broadcast). Neither polls, so an idle preview does no work for either.
 * - Settings resolve only when an input actually changes: resolveAll runs from a
 *   LaunchedEffect keyed on overrides, specs, preset, calibration and thermal tier.
 * - Capture-time objects (MediaActionSound, BitmapFactory.Options) are created per
 *   shutter press, and the thumbnail decodes downsampled on Dispatchers.IO.
 * - The pinch gesture no longer writes DataStore on every pointer event; see
 *   ZOOM_WRITE_EPSILON.
 *
 * Sustained-session behaviour still needs a device: see the M5 gate in BUILDLOG.md.
 */
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
    var chromeRevealToken by remember { mutableStateOf(0) }
    var chromeVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var isCalibrating by remember { mutableStateOf(false) }
    var liveCalibration by remember { mutableStateOf<Float?>(null) }
    var lowBatteryNotified by remember { mutableStateOf(false) }
    var showLowBattery by remember { mutableStateOf(false) }
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
    val edgelessPref by prefs.edgeless.collectAsState(initial = false)
    val isTrueSight by prefs.trueSight.collectAsState(initial = false)
    val brightnessAssist by prefs.brightnessAssist.collectAsState(initial = false)
    val calibrationPortrait by prefs.calibrationPortrait.collectAsState(initial = null)
    val calibrationLandscape by prefs.calibrationLandscape.collectAsState(initial = null)

    // TrueSight IS edgeless plus a scale calibration, so it implies the mode.
    val isEdgeless = edgelessPref || isTrueSight
    val activePresetId by prefs.activePresetId.collectAsState(initial = Presets.DEVICE_AUTO.id)
    val userPresets by prefs.userPresets.collectAsState(initial = emptyList())
    // Deleting the active user preset leaves an id nobody claims, which falls back
    // to Device Auto with every override still in place.
    val activePreset = remember(activePresetId, userPresets) {
        Presets.byId(activePresetId, userPresets)
    }

    // Thermal tier and battery level: observed, never polled per frame.
    val thermalTier by remember(context) { ThermalGovernor.tierFlow(context) }
        .collectAsState(initial = ThermalTier.NORMAL)
    val batteryPercent = rememberBatteryPercent()

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val storedCalibration = if (isPortrait) calibrationPortrait else calibrationLandscape

    // What the resolution layer composes onto the user's own zoom.
    val zoomCalibration = when {
        !isTrueSight -> EffectiveSettings.NO_CALIBRATION
        isCalibrating -> liveCalibration ?: storedCalibration ?: DEFAULT_CALIBRATION
        else -> storedCalibration ?: EffectiveSettings.NO_CALIBRATION
    }

    // The zoom the user dialled in, calibration excluded — what pinch and the
    // slider read and write, so editing never compounds calibration into itself.
    val userZoom = EffectiveSettings.userZoom(overrides, activePreset)

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

    // Window-level only: edgeless never touches the camera settings flow (Law 4).
    val view = LocalView.current
    LaunchedEffect(isEdgeless, view) {
        (context as? Activity)?.let { activity ->
            EdgelessController.apply(activity, isEdgeless, view)
        }
    }

    // Brightness assist rides on TrueSight and hands brightness back on exit.
    DisposableEffect(isTrueSight, brightnessAssist) {
        val activity = context as? Activity
        if (activity != null && isTrueSight && brightnessAssist) {
            ScreenBrightness.boost(activity)
        }
        onDispose { activity?.let { ScreenBrightness.restore(it) } }
    }

    // First run in this orientation opens calibration; an already-calibrated
    // orientation goes straight to the see-through view.
    LaunchedEffect(isTrueSight, isPortrait, storedCalibration) {
        if (isTrueSight && storedCalibration == null && !isCalibrating) {
            liveCalibration = DEFAULT_CALIBRATION
            isCalibrating = true
        }
    }

    // One warning per session, not a nag that returns every percent lost.
    LaunchedEffect(batteryPercent) {
        val percent = batteryPercent
        if (percent != null && percent < LOW_BATTERY_PERCENT && !lowBatteryNotified) {
            lowBatteryNotified = true
            showLowBattery = true
            delay(LOW_BATTERY_BANNER_MS)
            showLowBattery = false
        }
    }

    // The revealed chrome fades on its own; each fresh tap restarts the clock.
    LaunchedEffect(chromeVisible, chromeRevealToken) {
        if (chromeVisible) {
            delay(CHROME_REVEAL_MS)
            chromeVisible = false
        }
    }

    // Leaving edgeless hands the chrome back permanently.
    LaunchedEffect(isEdgeless) {
        if (!isEdgeless) chromeVisible = false
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

    if (showSettings) {
        SettingsScreen(
            calibrationPortrait = calibrationPortrait,
            calibrationLandscape = calibrationLandscape,
            brightnessAssist = brightnessAssist,
            onRecalibrate = {
                showSettings = false
                liveCalibration = storedCalibration ?: DEFAULT_CALIBRATION
                isCalibrating = true
                scope.launch { prefs.setTrueSight(true) }
            },
            onBrightnessAssistChange = { enabled ->
                scope.launch { prefs.setBrightnessAssist(enabled) }
            },
            onBack = { showSettings = false }
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
        LaunchedEffect(overrides, specs, isAbHolding, activePreset, zoomCalibration, thermalTier) {
            if (specs.isNotEmpty()) {
                val newResolved = EffectiveSettings.resolveAll(
                    specs = specs,
                    activePreset = activePreset,
                    overrides = if (isAbHolding) emptyMap() else overrides,
                    zoomCalibration = zoomCalibration,
                    thermalTier = thermalTier
                )
                resolved = newResolved
                cameraController.applySettings(newResolved)
            }
        }

        // Backgrounding hands the camera back to the system; CameraX releases and
        // rebinds the use cases around the lifecycle on its own. What it cannot do
        // is know our manual settings, so they are restated on every resume — the
        // rebound session would otherwise come back on defaults while the UI still
        // showed the user's values. Window mode is restated for the same reason:
        // system bars come back on their own after a task switch.
        val resolvedForResume by rememberUpdatedState(resolved)
        val edgelessForResume by rememberUpdatedState(isEdgeless)
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    cameraController.applySettings(resolvedForResume)
                    (context as? Activity)?.let { activity ->
                        EdgelessController.apply(activity, edgelessForResume, view)
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
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
        val currentIsMf by rememberUpdatedState(isMf)
        val currentUserZoom by rememberUpdatedState(userZoom)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoomEnabled, zoomRange, isCalibrating) {
                    // Calibration owns the drag while it is up.
                    if (!zoomEnabled || isCalibrating) return@pointerInput
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1.0f) {
                            // Pinch edits the user's own zoom, which then rides on
                            // top of any TrueSight calibration.
                            val newZoom = (currentUserZoom * zoomChange).coerceIn(zoomRange)
                            // A pinch delivers events far faster than the zoom can
                            // visibly change; writing only on a real difference keeps
                            // a gesture from becoming a burst of DataStore writes.
                            if (abs(newZoom - currentUserZoom) > ZOOM_WRITE_EPSILON) {
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
                }
                .pointerInput(isEdgeless) {
                    if (isEdgeless) {
                        // Edgeless owns the tap: one reveals the floating chrome,
                        // two exit. Focus keeps its gesture outside this mode so it
                        // never waits on the double-tap timeout.
                        detectTapGestures(
                            onTap = {
                                chromeVisible = true
                                chromeRevealToken++
                            },
                            onDoubleTap = {
                                scope.launch { prefs.setEdgeless(false) }
                            }
                        )
                    } else {
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
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        previewView.implementationMode =
                            PreviewView.ImplementationMode.COMPATIBLE
                        // Center-crop to the panel: accept the crop rather than
                        // letterboxing or stretching the frame.
                        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
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

            // Center MF Loupe Assist Overlay when adjusting manual focus slider.
            // It stands down while the device is throttling — it redraws on every
            // drag frame, which is exactly the load a hot phone does not need.
            if (isFocusTouching && thermalTier.allowsFocusLoupe) {
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

            // Calibration runs over the live preview so the user judges the real
            // scene, not a mock-up.
            if (isCalibrating) {
                CalibrationScreen(
                    initialFactor = liveCalibration ?: storedCalibration ?: DEFAULT_CALIBRATION,
                    isPortrait = isPortrait,
                    onFactorChange = { liveCalibration = it },
                    onConfirm = { factor ->
                        scope.launch { prefs.setCalibration(factor, isPortrait) }
                        liveCalibration = null
                        isCalibrating = false
                    },
                    onCancel = {
                        isCalibrating = false
                        liveCalibration = null
                        // Cancelling a first run leaves TrueSight rather than
                        // stranding the user in an uncalibrated see-through view.
                        if (storedCalibration == null) {
                            scope.launch { prefs.setTrueSight(false) }
                        }
                    }
                )
            }

            // Top bar: exposure readout chip + edgeless/controls/info icons.
            // Every piece of it is gone while edgeless — any chrome breaks the illusion.
            if (!isEdgeless && !isCalibrating) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        // The preview owns the panel edge to edge in every mode, so
                        // the chrome is what has to step around the system bars.
                        // Only outside edgeless: there the bars are hidden and any
                        // inset here would be the jump C14 forbids.
                        .statusBarsPadding()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Exposure readout chip
                    ExposureReadout(resolved)

                    Spacer(modifier = Modifier.weight(1f))

                    BatteryReadout(batteryPercent)

                    // Edgeless toggle
                    IconButton(onClick = { scope.launch { prefs.setEdgeless(true) } }) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Edgeless mode",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // TrueSight toggle — edgeless plus scale calibration
                    IconButton(onClick = { scope.launch { prefs.setTrueSight(true) } }) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "TrueSight mode",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Settings
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

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
            }

            // Status banners. In edgeless and TrueSight they ride the tap-reveal
            // layer rather than sitting permanently over the illusion.
            if (!isCalibrating && (!isEdgeless || chromeVisible)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .then(
                            if (isEdgeless) Modifier else Modifier.statusBarsPadding()
                        )
                        // Clear of the header row outside edgeless; a small drop
                        // from the top edge inside it.
                        .padding(top = if (isEdgeless) 24.dp else 56.dp)
                ) {
                    if (thermalTier == ThermalTier.SEVERE) {
                        StatusBanner(
                            "Device is hot — frame rate reduced and torch held off. " +
                                "Everything you've set is safe."
                        )
                    }
                    if (showLowBattery) {
                        StatusBanner("Battery below $LOW_BATTERY_PERCENT%.")
                    }
                    if (isEdgeless && batteryPercent != null) {
                        StatusBanner("Battery $batteryPercent%")
                    }
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
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Keeps the shutter clear of the gesture bar outside edgeless.
                    .then(
                        if (isEdgeless) Modifier else Modifier.navigationBarsPadding()
                    )
            ) {
                if (!isCalibrating &&
                    (!isEdgeless || chromeVisible) &&
                    showControls &&
                    specs.isNotEmpty()
                ) {
                    ControlsSheet(
                        specs = specs,
                        resolved = resolved,
                        overriddenIds = overrides.filterValues { it != null }.keys,
                        activePreset = activePreset,
                        userPresets = userPresets,
                        userZoomRatio = userZoom,
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
                        // Snapshot the current effective settings: preset and
                        // overrides collapsed into one flat set of values.
                        onSaveCurrentAsPreset = { name ->
                            scope.launch {
                                val saved = snapshotAsPreset(name, resolved)
                                prefs.saveUserPreset(saved)
                                prefs.setActivePresetId(saved.id)
                            }
                        },
                        onRenamePreset = { preset, newName ->
                            scope.launch {
                                prefs.saveUserPreset(preset.copy(displayName = newName))
                            }
                        },
                        onDeletePreset = { preset ->
                            scope.launch { prefs.deleteUserPreset(preset.id) }
                        },
                        onResetAll = { scope.launch { prefs.clearAllOverrides() } },
                        onAbHoldChange = { isAbHolding = it },
                        onFocusTouching = { isFocusTouching = it }
                    )
                }

                if (isCalibrating) {
                    // Calibration draws its own confirm bar.
                } else if (!isEdgeless) {
                    CaptureBar(
                        thumbnail = lastShotThumb,
                        isCapturing = isCapturing,
                        errorMessage = captureError,
                        onShutter = onShutter,
                        onThumbnailClick = {
                            lastShotUri?.let { uri -> openInSystemViewer(context, uri) }
                        }
                    )
                } else {
                    // Minimal floating layer: shutter, sheet handle, exit. Fades
                    // out on its own so the panel returns to pure image.
                    AnimatedVisibility(
                        visible = chromeVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        EdgelessChrome(
                            isCapturing = isCapturing,
                            isTrueSight = isTrueSight,
                            // Only offer the reset when their own zoom has moved
                            // off calibrated 1:1.
                            canResetZoom = userZoom > 1.001f,
                            onShutter = {
                                chromeRevealToken++
                                onShutter()
                            },
                            onToggleSheet = {
                                showControls = !showControls
                                chromeRevealToken++
                            },
                            onResetZoom = {
                                chromeRevealToken++
                                scope.launch { prefs.setOverride(ControlId.ZOOM_RATIO, null) }
                            },
                            onExit = {
                                scope.launch {
                                    prefs.setEdgeless(false)
                                    prefs.setTrueSight(false)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** How long revealed chrome lingers before fading, per C14. */
private const val CHROME_REVEAL_MS = 4000L

/**
 * Where the calibration drag starts on a first run — mid-range, so the user has
 * room to move in both directions before hitting a bound.
 */
private const val DEFAULT_CALIBRATION = 1.6f

private const val LOW_BATTERY_PERCENT = 15
private const val LOW_BATTERY_BANNER_MS = 6000L

/** Smallest zoom change worth persisting, in zoom ratio units. */
private const val ZOOM_WRITE_EPSILON = 0.005f

/**
 * Battery level, driven by the system's own sticky broadcast rather than by
 * polling — it updates when the level actually changes and costs nothing while
 * the preview runs.
 */
@Composable
private fun rememberBatteryPercent(): Int? {
    val context = LocalContext.current
    var percent by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    percent = level * 100 / scale
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // The sticky broadcast delivers the current level immediately. Android 14
        // requires the export flag; this one is a protected system broadcast, so
        // nothing outside the system should ever reach it.
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    return percent
}

@Composable
private fun BatteryReadout(percent: Int?) {
    if (percent == null) return
    Text(
        text = "$percent%",
        style = MaterialTheme.typography.bodySmall,
        color = if (percent < LOW_BATTERY_PERCENT) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        },
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

/**
 * A calm, non-blocking line of status. Never a dialog: nothing here is worth
 * interrupting a shot for.
 */
@Composable
private fun StatusBanner(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * The only chrome edgeless mode ever shows: shutter, sheet handle, and a way
 * out. Double-tapping the preview leaves edgeless too.
 */
@Composable
private fun EdgelessChrome(
    isCapturing: Boolean,
    isTrueSight: Boolean,
    canResetZoom: Boolean,
    onShutter: () -> Unit,
    onToggleSheet: () -> Unit,
    onResetZoom: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "TS" explains why zoom feels different in this mode.
        if (isTrueSight) {
            Text(
                text = "TS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        if (isTrueSight && canResetZoom) {
            IconButton(onClick = onResetZoom, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset zoom to calibrated scale",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        IconButton(onClick = onToggleSheet, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Controls",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(64.dp)
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

        IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.FullscreenExit,
                contentDescription = "Exit edgeless mode",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Freezes the current effective settings into a named user preset. Saved values
 * are re-clamped against the device caps whenever they are loaded, so a preset
 * captured on a FULL device degrades gracefully on a narrower one.
 */
private fun snapshotAsPreset(
    name: String,
    resolved: Map<ControlId, EffectiveSettings.Resolution>
): Preset = Preset(
    id = PresetCodec.idForName(name),
    displayName = PresetCodec.sanitizeName(name),
    description = "Saved from the current settings",
    values = EffectiveSettings.snapshot(resolved)
)

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
