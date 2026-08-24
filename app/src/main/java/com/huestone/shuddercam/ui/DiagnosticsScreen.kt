package com.huestone.shuddercam.ui

import android.hardware.camera2.CameraMetadata
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huestone.shuddercam.camera.CameraCaps

@Composable
fun DiagnosticsScreen(
    allCaps: List<CameraCaps>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The window draws edge to edge, so full screens inset themselves.
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Camera Diagnostics",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (allCaps.isEmpty()) {
            Text(
                text = "No camera data available. Grant camera permission and return.",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            allCaps.forEach { caps ->
                CameraSection(caps)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CameraSection(caps: CameraCaps) {
    SectionHeader("Camera ${caps.cameraId} — ${caps.lensFacing}")

    DiagRow("Hardware Level", caps.hardwareLevel.displayName)

    GroupHeader("Exposure")
    DiagRow("ISO Range", caps.sensitivityRange?.let { "${it.lower}–${it.upper}" } ?: "Unsupported")
    DiagRow("Exposure Time", caps.exposureTimeRange?.let {
        "${formatNs(it.lower)}–${formatNs(it.upper)}"
    } ?: "Unsupported")
    DiagRow("AE Compensation", caps.aeCompensationRange?.let {
        "${it.lower}..${it.upper} (step ${caps.aeCompensationStep ?: "?"})"
    } ?: "Unsupported")
    DiagRow("AE Modes", formatAeModes(caps.availableAeModes))
    DiagRow("AE Lock", if (caps.aeLockAvailable) "Yes" else "No")

    GroupHeader("Focus")
    DiagRow("Min Focus Distance", caps.minimumFocusDistance?.let {
        if (it == 0f) "Fixed focus" else "$it diopters"
    } ?: "Unsupported")
    DiagRow("Calibration", formatFocusCalibration(caps.focusDistanceCalibration))
    DiagRow("AF Modes", formatAfModes(caps.availableAfModes))

    GroupHeader("White Balance")
    DiagRow("AWB Modes", formatAwbModes(caps.availableAwbModes))
    DiagRow("AWB Lock", if (caps.awbLockAvailable) "Yes" else "No")

    GroupHeader("Zoom")
    DiagRow("Zoom Ratio Range", caps.zoomRatioRange?.let {
        "${it.lower}x–${it.upper}x"
    } ?: "Unsupported")

    GroupHeader("Flash")
    DiagRow("Flash", if (caps.flashAvailable) "Available" else "Not available")

    GroupHeader("Frame Rate")
    DiagRow("FPS Ranges", caps.supportedFpsRanges.joinToString(", ") {
        "${it.lower}–${it.upper}"
    }.ifEmpty { "None" })

    GroupHeader("Stabilization")
    DiagRow("Video Stabilization", formatVideoStabModes(caps.availableVideoStabilizationModes))
    DiagRow("Optical Stabilization", formatOisModes(caps.availableOpticalStabilizationModes))

    GroupHeader("Capture")
    DiagRow("RAW Capture", if (caps.hasRawCapability) "Supported" else "Not supported")
    DiagRow("Max JPEG", caps.maxJpegResolution?.let {
        "${it.width}×${it.height}"
    } ?: "Unknown")

    if (caps.physicalCameraIds.isNotEmpty()) {
        GroupHeader("Physical Cameras")
        DiagRow("IDs", caps.physicalCameraIds.joinToString(", "))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun GroupHeader(text: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(0.6f)
        )
    }
}

private fun formatNs(ns: Long): String {
    return when {
        ns >= 1_000_000_000L -> String.format("%.1fs", ns / 1_000_000_000.0)
        ns >= 1_000_000L -> String.format("%.1fms", ns / 1_000_000.0)
        ns >= 1_000L -> String.format("%.1fµs", ns / 1_000.0)
        else -> "${ns}ns"
    }
}

private fun formatAeModes(modes: List<Int>): String {
    if (modes.isEmpty()) return "None"
    return modes.joinToString(", ") { mode ->
        when (mode) {
            CameraMetadata.CONTROL_AE_MODE_OFF -> "Off"
            CameraMetadata.CONTROL_AE_MODE_ON -> "On"
            CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH -> "Auto Flash"
            CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "Always Flash"
            CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "Redeye"
            else -> "Mode $mode"
        }
    }
}

private fun formatAfModes(modes: List<Int>): String {
    if (modes.isEmpty()) return "None"
    return modes.joinToString(", ") { mode ->
        when (mode) {
            CameraMetadata.CONTROL_AF_MODE_OFF -> "Off (Manual)"
            CameraMetadata.CONTROL_AF_MODE_AUTO -> "Auto"
            CameraMetadata.CONTROL_AF_MODE_MACRO -> "Macro"
            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "Continuous Video"
            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "Continuous Picture"
            CameraMetadata.CONTROL_AF_MODE_EDOF -> "EDOF"
            else -> "Mode $mode"
        }
    }
}

private fun formatAwbModes(modes: List<Int>): String {
    if (modes.isEmpty()) return "None"
    return modes.joinToString(", ") { mode ->
        when (mode) {
            CameraMetadata.CONTROL_AWB_MODE_OFF -> "Off (Manual)"
            CameraMetadata.CONTROL_AWB_MODE_AUTO -> "Auto"
            CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> "Incandescent"
            CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT -> "Fluorescent"
            CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "Warm Fluorescent"
            CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> "Daylight"
            CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "Cloudy"
            CameraMetadata.CONTROL_AWB_MODE_TWILIGHT -> "Twilight"
            CameraMetadata.CONTROL_AWB_MODE_SHADE -> "Shade"
            else -> "Mode $mode"
        }
    }
}

private fun formatFocusCalibration(calibration: Int?): String = when (calibration) {
    CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED -> "Uncalibrated"
    CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE -> "Approximate"
    CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED -> "Calibrated"
    null -> "Unknown"
    else -> "Value $calibration"
}

private fun formatVideoStabModes(modes: List<Int>): String {
    if (modes.isEmpty()) return "None"
    return modes.joinToString(", ") { mode ->
        when (mode) {
            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> "Off"
            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON -> "On"
            else -> "Mode $mode"
        }
    }
}

private fun formatOisModes(modes: List<Int>): String {
    if (modes.isEmpty()) return "None"
    return modes.joinToString(", ") { mode ->
        when (mode) {
            CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF -> "Off"
            CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON -> "On"
            else -> "Mode $mode"
        }
    }
}
