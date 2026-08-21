package com.huestone.shuddercam.camera

import android.content.Context
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * Lifecycle-aware camera binding. Manages a CameraX Preview use case
 * bound to the rear camera by default. Probes capabilities for all
 * cameras on bind and exposes them as immutable CameraCaps objects.
 */
@OptIn(ExperimentalCamera2Interop::class)
class CameraController(private val context: Context) {

    companion object {
        private const val TAG = "CameraController"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraControl: CameraControl? = null
    private var preview: Preview? = null

    /** All probed camera capabilities, populated on bind. */
    var allCaps: List<CameraCaps> = emptyList()
        private set

    /** Caps for the currently bound camera (default back). */
    var activeCaps: CameraCaps? = null
        private set

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCapsReady: (List<CameraCaps>) -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            // Probe all cameras
            allCaps = probeAllCameras(provider)
            allCaps.forEach { caps ->
                Log.i(TAG, "Camera ${caps.cameraId} [${caps.lensFacing}]: " +
                    "hw=${caps.hardwareLevel.displayName}, " +
                    "ISO=${caps.sensitivityRange ?: "N/A"}, " +
                    "focus=${caps.minimumFocusDistance ?: "N/A"}, " +
                    "zoom=${caps.zoomRatioRange ?: "N/A"}, " +
                    "flash=${caps.flashAvailable}, " +
                    "raw=${caps.hasRawCapability}")
            }

            preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            provider.unbindAll()

            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview!!
            )

            cameraControl = camera.cameraControl

            // Get active camera caps
            val camera2Info = Camera2CameraInfo.from(camera.cameraInfo)
            activeCaps = allCaps.find { it.cameraId == camera2Info.cameraId }

            onCapsReady(allCaps)
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraControl = null
        preview = null
    }

    /**
     * Tap-to-focus: creates a metering point from the given coordinates
     * and starts a focus/metering action.
     */
    fun tapToFocus(factory: MeteringPointFactory, x: Float, y: Float) {
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        cameraControl?.startFocusAndMetering(action)
    }

    private fun probeAllCameras(provider: ProcessCameraProvider): List<CameraCaps> {
        val results = mutableListOf<CameraCaps>()
        val cameraInfoList = provider.availableCameraInfos

        for (cameraInfo in cameraInfoList) {
            try {
                val camera2Info = Camera2CameraInfo.from(cameraInfo)
                val cameraId = camera2Info.cameraId
                val chars = camera2Info.getCameraCharacteristic(
                    android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
                )
                // Get full characteristics via Camera2CameraInfo
                val caps = probeCamera(cameraId, camera2Info)
                results.add(caps)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to probe camera: ${e.message}")
            }
        }

        return results
    }

    private fun probeCamera(cameraId: String, camera2Info: Camera2CameraInfo): CameraCaps {
        val hwLevel = HardwareLevel.from(
            camera2Info.getCameraCharacteristic(
                android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
            ) ?: android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
        )

        val facing = when (camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.LENS_FACING
        )) {
            android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK -> "Back"
            android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

        val sensitivityRange = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        )
        val exposureTimeRange = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        )
        val aeCompRange = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
        )
        val aeCompStep = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP
        )?.toFloat()
        val aeModes = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES
        )?.toList() ?: emptyList()
        val aeLock = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE
        ) ?: false

        val minFocusDist = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        )
        val focusCalibration = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION
        )
        val afModes = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
        )?.toList() ?: emptyList()

        val awbModes = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
        )?.toList() ?: emptyList()
        val awbLock = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE
        ) ?: false

        val zoomRange = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE
        )

        val flash = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
        ) ?: false

        val fpsRanges = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        )?.toList() ?: emptyList()

        val videoStab = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        )?.toList() ?: emptyList()
        val ois = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        )?.toList() ?: emptyList()

        val capabilities = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        )?.toList() ?: emptyList()
        val hasRaw = capabilities.contains(
            android.hardware.camera2.CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW
        )

        val streamMap = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )
        val jpegSizes = streamMap?.getOutputSizes(android.graphics.ImageFormat.JPEG)
        val maxJpeg = jpegSizes?.maxByOrNull { it.width * it.height }

        return CameraCaps(
            cameraId = cameraId,
            lensFacing = facing,
            hardwareLevel = hwLevel,
            sensitivityRange = sensitivityRange,
            exposureTimeRange = exposureTimeRange,
            aeCompensationRange = aeCompRange,
            aeCompensationStep = aeCompStep,
            availableAeModes = aeModes,
            aeLockAvailable = aeLock,
            minimumFocusDistance = minFocusDist,
            focusDistanceCalibration = focusCalibration,
            availableAfModes = afModes,
            availableAwbModes = awbModes,
            awbLockAvailable = awbLock,
            zoomRatioRange = zoomRange,
            flashAvailable = flash,
            supportedFpsRanges = fpsRanges,
            availableVideoStabilizationModes = videoStab,
            availableOpticalStabilizationModes = ois,
            hasRawCapability = hasRaw,
            maxJpegResolution = maxJpeg,
            physicalCameraIds = emptySet()
        )
    }
}
