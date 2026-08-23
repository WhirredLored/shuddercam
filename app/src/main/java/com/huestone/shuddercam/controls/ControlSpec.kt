package com.huestone.shuddercam.controls

/**
 * Plain-Kotlin mirror of CameraCaps so controls/ stays free of Android imports.
 * camera/ maps CameraCaps into this; controls/ uses it for spec derivation.
 */
data class CapsMirror(
    val sensitivityRange: IntRange?,
    val exposureTimeRange: LongRange?,
    val aeCompensationRange: IntRange?,
    val aeCompensationStep: Float?,
    val availableAeModes: List<Int>,
    val aeLockAvailable: Boolean,
    val minimumFocusDistance: Float?,
    val availableAfModes: List<Int>,
    val availableAwbModes: List<Int>,
    val awbLockAvailable: Boolean,
    val zoomRatioRange: ClosedFloatingPointRange<Float>?,
    val flashAvailable: Boolean,
    val supportedFpsRanges: List<Pair<Int, Int>>,
    val availableVideoStabilizationModes: List<Int>,
    val availableOpticalStabilizationModes: List<Int>,
    val hasRawCapability: Boolean
)

/**
 * Runtime spec for a single control: whether it's available,
 * and its bounds for clamping.
 */
data class ControlSpec(
    val id: ControlId,
    val available: Boolean,
    val intRange: IntRange? = null,
    val longRange: LongRange? = null,
    val floatRange: ClosedFloatingPointRange<Float>? = null,
    val allowedIntValues: List<Int>? = null,
    val allowedStringValues: List<String>? = null,
    val allowedIntRangeValues: List<Pair<Int, Int>>? = null
) {
    companion object {
        /**
         * Derives a full set of ControlSpecs from a CapsMirror.
         * Pure function — no Android types.
         */
        fun deriveAll(caps: CapsMirror): Map<ControlId, ControlSpec> {
            val specs = mutableMapOf<ControlId, ControlSpec>()

            // AE_MODE: available if device has more than just auto
            specs[ControlId.AE_MODE] = ControlSpec(
                id = ControlId.AE_MODE,
                available = caps.availableAeModes.isNotEmpty(),
                allowedIntValues = caps.availableAeModes
            )

            // ISO
            specs[ControlId.ISO] = ControlSpec(
                id = ControlId.ISO,
                available = caps.sensitivityRange != null,
                intRange = caps.sensitivityRange
            )

            // SHUTTER_NS
            specs[ControlId.SHUTTER_NS] = ControlSpec(
                id = ControlId.SHUTTER_NS,
                available = caps.exposureTimeRange != null,
                longRange = caps.exposureTimeRange
            )

            // AE_COMPENSATION
            specs[ControlId.AE_COMPENSATION] = ControlSpec(
                id = ControlId.AE_COMPENSATION,
                available = caps.aeCompensationRange != null,
                intRange = caps.aeCompensationRange
            )

            // AE_LOCK
            specs[ControlId.AE_LOCK] = ControlSpec(
                id = ControlId.AE_LOCK,
                available = caps.aeLockAvailable
            )

            // AF_MODE
            specs[ControlId.AF_MODE] = ControlSpec(
                id = ControlId.AF_MODE,
                available = caps.availableAfModes.isNotEmpty(),
                allowedIntValues = caps.availableAfModes
            )

            // FOCUS_DISTANCE
            val hasMf = caps.minimumFocusDistance != null && caps.minimumFocusDistance > 0f
            specs[ControlId.FOCUS_DISTANCE] = ControlSpec(
                id = ControlId.FOCUS_DISTANCE,
                available = hasMf,
                floatRange = if (hasMf) 0f..caps.minimumFocusDistance!! else null
            )

            // AWB_MODE
            specs[ControlId.AWB_MODE] = ControlSpec(
                id = ControlId.AWB_MODE,
                available = caps.availableAwbModes.isNotEmpty(),
                allowedIntValues = caps.availableAwbModes
            )

            // AWB_LOCK
            specs[ControlId.AWB_LOCK] = ControlSpec(
                id = ControlId.AWB_LOCK,
                available = caps.awbLockAvailable
            )

            // COLOR_TEMP_K: available only if AWB OFF mode is available
            val awbOffAvailable = caps.availableAwbModes.contains(0) // AWB_MODE_OFF = 0
            specs[ControlId.COLOR_TEMP_K] = ControlSpec(
                id = ControlId.COLOR_TEMP_K,
                available = awbOffAvailable,
                intRange = if (awbOffAvailable) 2000..10000 else null
            )

            // ZOOM_RATIO
            specs[ControlId.ZOOM_RATIO] = ControlSpec(
                id = ControlId.ZOOM_RATIO,
                available = caps.zoomRatioRange != null,
                floatRange = caps.zoomRatioRange
            )

            // TORCH_MODE
            specs[ControlId.TORCH_MODE] = ControlSpec(
                id = ControlId.TORCH_MODE,
                available = caps.flashAvailable
            )

            // FLASH_MODE: fires at capture only, distinct from the always-on torch.
            // Values mirror CameraX ImageCapture: 0 = auto, 1 = on, 2 = off.
            specs[ControlId.FLASH_MODE] = ControlSpec(
                id = ControlId.FLASH_MODE,
                available = caps.flashAvailable,
                allowedIntValues = if (caps.flashAvailable) listOf(0, 1, 2) else null
            )

            // FPS_RANGE: only the ranges the device actually reports are selectable
            val fpsOptions = caps.supportedFpsRanges
                .distinct()
                .sortedWith(compareBy({ it.second }, { it.first }))
            specs[ControlId.FPS_RANGE] = ControlSpec(
                id = ControlId.FPS_RANGE,
                available = fpsOptions.isNotEmpty(),
                allowedIntRangeValues = fpsOptions.ifEmpty { null }
            )

            // STABILIZATION_MODE: probed video stabilization modes. A device that
            // reports a single mode has nothing to choose, so the control is absent.
            val stabModes = caps.availableVideoStabilizationModes.distinct().sorted()
            val hasStab = stabModes.size > 1
            specs[ControlId.STABILIZATION_MODE] = ControlSpec(
                id = ControlId.STABILIZATION_MODE,
                available = hasStab,
                allowedIntValues = if (hasStab) stabModes else null
            )

            // CAPTURE_FORMAT: absent. The capture pipeline writes JPEG only, so
            // there is nothing to choose between even on a camera that reports RAW
            // support — the probe still surfaces that fact in diagnostics.
            // See DECISIONS.md for the DNG limitation behind this.
            specs[ControlId.CAPTURE_FORMAT] = ControlSpec(
                id = ControlId.CAPTURE_FORMAT,
                available = false,
                allowedStringValues = listOf("JPEG")
            )

            // GRID_OVERLAY (always available — it's a UI-only control)
            specs[ControlId.GRID_OVERLAY] = ControlSpec(
                id = ControlId.GRID_OVERLAY,
                available = true
            )

            return specs
        }
    }
}
