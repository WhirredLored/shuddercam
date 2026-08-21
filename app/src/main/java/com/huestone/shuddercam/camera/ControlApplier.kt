package com.huestone.shuddercam.camera

import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
     * Only writes exposure-related controls for now (C06); extended by later commits.
     */
    fun apply(
        camera2Control: Camera2CameraControl,
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

        if (applied) {
            camera2Control.setCaptureRequestOptions(builder.build())
        }
    }
}
