package com.huestone.shuddercam.camera

import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import com.huestone.shuddercam.controls.ControlId
import com.huestone.shuddercam.controls.ControlValue
import com.huestone.shuddercam.controls.EffectiveSettings

/**
 * ONE WRITER (Law 4): translates EffectiveSettings into Camera2
 * CaptureRequestOptions and applies them via Camera2CameraControl.
 * UI never touches Camera2 types directly.
 */
@OptIn(ExperimentalCamera2Interop::class)
object ControlApplier {

    private const val TAG = "ControlApplier"

    /**
     * Applies the resolved settings to the camera.
     *
     * Most controls are Camera2 capture-request options. Zoom and torch are
     * owned by CameraX itself, so they go through [cameraControl] — writing
     * CONTROL_ZOOM_RATIO / FLASH_MODE through interop would fight CameraX's
     * own state for the same two knobs. Flash-at-capture lives on
     * [imageCapture], which is null until the capture use case is bound.
     */
    fun apply(
        camera2Control: Camera2CameraControl,
        cameraControl: CameraControl,
        imageCapture: ImageCapture?,
        resolved: Map<ControlId, EffectiveSettings.Resolution>
    ) {
        val builder = CaptureRequestOptions.Builder()
        var applied = false

        // AE_MODE
        val aeRes = resolved[ControlId.AE_MODE]
        if (aeRes is EffectiveSettings.Resolution.Resolved) {
            val aeMode = (aeRes.value as? ControlValue.IntValue)?.value
            if (aeMode != null) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, aeMode)
                applied = true

                if (aeRes.wasClamped) {
                    Log.w(TAG, "AE_MODE clamped to $aeMode")
                }
            }
        }

        // Determine if AE is off (manual exposure)
        val isManual = (aeRes as? EffectiveSettings.Resolution.Resolved)
            ?.let { (it.value as? ControlValue.IntValue)?.value == 0 }
            ?: false

        if (isManual) {
            // ISO (SENSOR_SENSITIVITY) — only when AE is off
            val isoRes = resolved[ControlId.ISO]
            if (isoRes is EffectiveSettings.Resolution.Resolved) {
                val iso = (isoRes.value as? ControlValue.IntValue)?.value
                if (iso != null && iso > 0) {
                    builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                    applied = true
                    if (isoRes.wasClamped) Log.w(TAG, "ISO clamped to $iso")
                }
            }

            // SHUTTER_NS (SENSOR_EXPOSURE_TIME) — only when AE is off
            val shutterRes = resolved[ControlId.SHUTTER_NS]
            if (shutterRes is EffectiveSettings.Resolution.Resolved) {
                val ns = (shutterRes.value as? ControlValue.LongValue)?.value
                if (ns != null && ns > 0) {
                    builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, ns)
                    applied = true
                    if (shutterRes.wasClamped) Log.w(TAG, "SHUTTER_NS clamped to $ns")
                }
            }
        }

        // AE_COMPENSATION — applicable when AE is on
        if (!isManual) {
            val evRes = resolved[ControlId.AE_COMPENSATION]
            if (evRes is EffectiveSettings.Resolution.Resolved) {
                val ev = (evRes.value as? ControlValue.IntValue)?.value
                if (ev != null) {
                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev)
                    applied = true
                    if (evRes.wasClamped) Log.w(TAG, "AE_COMPENSATION clamped to $ev")
                }
            }
        }

        // AE_LOCK
        val aeLockRes = resolved[ControlId.AE_LOCK]
        if (aeLockRes is EffectiveSettings.Resolution.Resolved) {
            val locked = (aeLockRes.value as? ControlValue.BoolValue)?.value
            if (locked != null) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
                applied = true
            }
        }

        // ── Focus family (C07) ──

        // AF_MODE
        val afRes = resolved[ControlId.AF_MODE]
        if (afRes is EffectiveSettings.Resolution.Resolved) {
            val afMode = (afRes.value as? ControlValue.IntValue)?.value
            if (afMode != null) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, afMode)
                applied = true
                if (afRes.wasClamped) Log.w(TAG, "AF_MODE clamped to $afMode")
            }
        }

        // Determine if AF is off (manual focus)
        val isMf = (afRes as? EffectiveSettings.Resolution.Resolved)
            ?.let { (it.value as? ControlValue.IntValue)?.value == 0 }
            ?: false

        if (isMf) {
            // FOCUS_DISTANCE (LENS_FOCUS_DISTANCE) — only when AF is off
            val fdRes = resolved[ControlId.FOCUS_DISTANCE]
            if (fdRes is EffectiveSettings.Resolution.Resolved) {
                val dist = (fdRes.value as? ControlValue.FloatValue)?.value
                if (dist != null) {
                    builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, dist)
                    applied = true
                    if (fdRes.wasClamped) Log.w(TAG, "FOCUS_DISTANCE clamped to $dist")
                }
            }
        }

        // ── White Balance family (C08) ──

        val awbRes = resolved[ControlId.AWB_MODE]
        if (awbRes is EffectiveSettings.Resolution.Resolved) {
            val awbMode = (awbRes.value as? ControlValue.IntValue)?.value
            if (awbMode != null) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)
                applied = true
                if (awbRes.wasClamped) Log.w(TAG, "AWB_MODE clamped to $awbMode")
            }
        }

        val isManualWb = (awbRes as? EffectiveSettings.Resolution.Resolved)
            ?.let { (it.value as? ControlValue.IntValue)?.value == 0 }
            ?: false

        if (isManualWb) {
            // Disable AWB and apply color gains from Kelvin slider
            builder.setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_MODE,
                android.hardware.camera2.CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
            )
            val kelvinRes = resolved[ControlId.COLOR_TEMP_K]
            if (kelvinRes is EffectiveSettings.Resolution.Resolved) {
                val kelvin = (kelvinRes.value as? ControlValue.IntValue)?.value ?: 5500
                val gains = com.huestone.shuddercam.controls.ColorTemperature.kelvinToRggbGains(kelvin)
                val rggbVector = android.hardware.camera2.params.RggbChannelVector(
                    gains.red,
                    gains.greenEven,
                    gains.greenOdd,
                    gains.blue
                )
                builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, rggbVector)
                applied = true
            }
        } else {
            // AWB_LOCK — only when AWB is active
            val awbLockRes = resolved[ControlId.AWB_LOCK]
            if (awbLockRes is EffectiveSettings.Resolution.Resolved) {
                val locked = (awbLockRes.value as? ControlValue.BoolValue)?.value
                if (locked != null) {
                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, locked)
                    applied = true
                }
            }
        }

        // ── Motion family (C09) ──

        // FPS_RANGE
        val fpsRes = resolved[ControlId.FPS_RANGE]
        if (fpsRes is EffectiveSettings.Resolution.Resolved) {
            val fps = fpsRes.value as? ControlValue.IntRangeValue
            if (fps != null) {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(fps.lower, fps.upper)
                )
                applied = true
                if (fpsRes.wasClamped) {
                    Log.w(TAG, "FPS_RANGE clamped to ${fps.lower}-${fps.upper}")
                }
            }
        }

        // STABILIZATION_MODE
        val stabRes = resolved[ControlId.STABILIZATION_MODE]
        if (stabRes is EffectiveSettings.Resolution.Resolved) {
            val mode = (stabRes.value as? ControlValue.IntValue)?.value
            if (mode != null) {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    mode
                )
                applied = true
                if (stabRes.wasClamped) Log.w(TAG, "STABILIZATION_MODE clamped to $mode")
            }
        }

        if (applied) {
            camera2Control.setCaptureRequestOptions(builder.build())
        }

        // ── CameraX-owned controls ──

        // ZOOM_RATIO
        val zoomRes = resolved[ControlId.ZOOM_RATIO]
        if (zoomRes is EffectiveSettings.Resolution.Resolved) {
            val zoom = (zoomRes.value as? ControlValue.FloatValue)?.value
            if (zoom != null) {
                cameraControl.setZoomRatio(zoom)
                if (zoomRes.wasClamped) Log.w(TAG, "ZOOM_RATIO clamped to $zoom")
            }
        }

        // TORCH_MODE
        val torchRes = resolved[ControlId.TORCH_MODE]
        if (torchRes is EffectiveSettings.Resolution.Resolved) {
            val torchOn = (torchRes.value as? ControlValue.BoolValue)?.value
            if (torchOn != null) {
                cameraControl.enableTorch(torchOn)
            }
        }

        // FLASH_MODE — fires at capture only, so it lives on the capture use case
        val flashRes = resolved[ControlId.FLASH_MODE]
        if (imageCapture != null && flashRes is EffectiveSettings.Resolution.Resolved) {
            val mode = (flashRes.value as? ControlValue.IntValue)?.value
            if (mode != null) {
                imageCapture.flashMode = mode
                if (flashRes.wasClamped) Log.w(TAG, "FLASH_MODE clamped to $mode")
            }
        }
    }
}
