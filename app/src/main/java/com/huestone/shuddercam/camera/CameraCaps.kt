package com.huestone.shuddercam.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size

/**
 * Hardware level enum mapping Camera2 hardware level constants to readable names.
 */
enum class HardwareLevel(val displayName: String) {
    LEGACY("Legacy"),
    LIMITED("Limited"),
    FULL("Full"),
    LEVEL_3("Level 3"),
    EXTERNAL("External"),
    UNKNOWN("Unknown");

    companion object {
        fun from(level: Int): HardwareLevel = when (level) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> LEGACY
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> LIMITED
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> FULL
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> LEVEL_3
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> EXTERNAL
            else -> UNKNOWN
        }
    }
}

/**
 * Immutable capability snapshot for a single camera.
 * Every field that a LEGACY device can't report is nullable or empty —
 * never a fake default. Downstream code can distinguish "unsupported" from "zero."
 */
data class CameraCaps(
    val cameraId: String,
    val lensFacing: String,
    val hardwareLevel: HardwareLevel,

    // Exposure
    val sensitivityRange: Range<Int>?,
    val exposureTimeRange: Range<Long>?,
    val aeCompensationRange: Range<Int>?,
    val aeCompensationStep: Float?,
    val availableAeModes: List<Int>,
    val aeLockAvailable: Boolean,

    // Focus
    val minimumFocusDistance: Float?,
    val focusDistanceCalibration: Int?,
    val availableAfModes: List<Int>,

    // White balance
    val availableAwbModes: List<Int>,
    val awbLockAvailable: Boolean,

    // Zoom
    val zoomRatioRange: Range<Float>?,

    // Flash
    val flashAvailable: Boolean,

    // FPS
    val supportedFpsRanges: List<Range<Int>>,

    // Stabilization
    val availableVideoStabilizationModes: List<Int>,
    val availableOpticalStabilizationModes: List<Int>,

    // Capture capabilities
    val hasRawCapability: Boolean,
    val maxJpegResolution: Size?,

    // Physical cameras
    val physicalCameraIds: Set<String>
) {
    companion object {
        /**
         * Probes a single camera by its characteristics.
         */
        fun probe(cameraId: String, chars: CameraCharacteristics): CameraCaps {
            val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                else -> "Unknown"
            }

            val hwLevel = HardwareLevel.from(
                chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
            )

            // Exposure
            val sensitivityRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val exposureTimeRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val aeCompRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            val aeCompStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat()
            val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.toList()
                ?: emptyList()
            val aeLock = chars.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) ?: false

            // Focus
            val minFocusDist = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
            val focusCalibration = chars.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)
            val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList()
                ?: emptyList()

            // White balance
            val awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList()
                ?: emptyList()
            val awbLock = chars.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) ?: false

            // Zoom
            val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)

            // Flash
            val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

            // FPS ranges
            val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.toList() ?: emptyList()

            // Stabilization
            val videoStab = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                ?.toList() ?: emptyList()
            val ois = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                ?.toList() ?: emptyList()

            // RAW capability
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.toList() ?: emptyList()
            val hasRaw = capabilities.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW
            )

            // Max JPEG resolution
            val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = streamMap?.getOutputSizes(android.graphics.ImageFormat.JPEG)
            val maxJpeg = jpegSizes?.maxByOrNull { it.width * it.height }

            // Physical cameras
            val physicalIds = chars.physicalCameraIds

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
                physicalCameraIds = physicalIds
            )
        }
    }
}
