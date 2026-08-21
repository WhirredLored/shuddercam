package com.huestone.shuddercam.controls

/**
 * THE resolution function (Law 2):
 *   effective(id) = userOverride(id) ?: activePreset.value(id) ?: deviceAuto(id)
 * plus clamping of every resolved value into the ControlSpec bounds (Law 5).
 */
object EffectiveSettings {

    /**
     * Result for a single control: either a resolved value (possibly clamped),
     * or Absent if the control is unsupported on this device.
     */
    sealed class Resolution {
        data class Resolved(val value: ControlValue, val wasClamped: Boolean = false) : Resolution()
        data object Absent : Resolution()
    }

    /**
     * Resolves the effective value for a single control.
     */
    fun resolve(
        id: ControlId,
        specs: Map<ControlId, ControlSpec>,
        activePreset: Preset,
        overrides: Map<ControlId, ControlValue?>
    ): Resolution {
        val spec = specs[id]
        if (spec == null || !spec.available) return Resolution.Absent

        // Resolution chain: override → preset → device auto (null)
        val override = overrides[id]
        val raw = override ?: activePreset.value(id)

        if (raw == null) {
            // Device auto — no explicit value
            return Resolution.Resolved(deviceAuto(id))
        }

        // Clamp to spec bounds
        return clamp(raw, spec)
    }

    /**
     * Resolves all controls at once.
     */
    fun resolveAll(
        specs: Map<ControlId, ControlSpec>,
        activePreset: Preset,
        overrides: Map<ControlId, ControlValue?>
    ): Map<ControlId, Resolution> {
        return ControlId.entries.associateWith { id ->
            resolve(id, specs, activePreset, overrides)
        }
    }

    /**
     * Default "device auto" value for a control when neither override nor preset
     * provides a value. These are the safe defaults that let the camera auto-adjust.
     */
    private fun deviceAuto(id: ControlId): ControlValue = when (id) {
        ControlId.AE_MODE -> ControlValue.IntValue(1) // AE_MODE_ON
        ControlId.ISO -> ControlValue.IntValue(0) // 0 = auto sense
        ControlId.SHUTTER_NS -> ControlValue.LongValue(0L) // 0 = auto
        ControlId.AE_COMPENSATION -> ControlValue.IntValue(0)
        ControlId.AE_LOCK -> ControlValue.BoolValue(false)
        ControlId.AF_MODE -> ControlValue.IntValue(4) // AF_MODE_CONTINUOUS_PICTURE
        ControlId.FOCUS_DISTANCE -> ControlValue.FloatValue(0f) // infinity
        ControlId.AWB_MODE -> ControlValue.IntValue(1) // AWB_MODE_AUTO
        ControlId.AWB_LOCK -> ControlValue.BoolValue(false)
        ControlId.COLOR_TEMP_K -> ControlValue.IntValue(5500) // daylight
        ControlId.ZOOM_RATIO -> ControlValue.FloatValue(1.0f)
        ControlId.TORCH_MODE -> ControlValue.BoolValue(false)
        ControlId.FPS_RANGE -> ControlValue.IntRangeValue(15, 30)
        ControlId.STABILIZATION_MODE -> ControlValue.IntValue(0) // off
        ControlId.CAPTURE_FORMAT -> ControlValue.StringValue("JPEG")
        ControlId.GRID_OVERLAY -> ControlValue.BoolValue(false)
    }

    /**
     * Clamps a value to the spec bounds per Law 5.
     * Returns Resolved with wasClamped=true if clamping occurred.
     */
    internal fun clamp(value: ControlValue, spec: ControlSpec): Resolution {
        return when (value) {
            is ControlValue.IntValue -> {
                val range = spec.intRange
                if (range != null) {
                    val clamped = value.value.coerceIn(range)
                    Resolution.Resolved(
                        ControlValue.IntValue(clamped),
                        wasClamped = clamped != value.value
                    )
                } else if (spec.allowedIntValues != null && value.value !in spec.allowedIntValues) {
                    // Clamp to nearest allowed value
                    val nearest = spec.allowedIntValues.minByOrNull {
                        kotlin.math.abs(it - value.value)
                    } ?: value.value
                    Resolution.Resolved(
                        ControlValue.IntValue(nearest),
                        wasClamped = nearest != value.value
                    )
                } else {
                    Resolution.Resolved(value)
                }
            }
            is ControlValue.LongValue -> {
                val range = spec.longRange
                if (range != null) {
                    val clamped = value.value.coerceIn(range)
                    Resolution.Resolved(
                        ControlValue.LongValue(clamped),
                        wasClamped = clamped != value.value
                    )
                } else {
                    Resolution.Resolved(value)
                }
            }
            is ControlValue.FloatValue -> {
                val range = spec.floatRange
                if (range != null) {
                    val clamped = value.value.coerceIn(range)
                    Resolution.Resolved(
                        ControlValue.FloatValue(clamped),
                        wasClamped = clamped != value.value
                    )
                } else {
                    Resolution.Resolved(value)
                }
            }
            is ControlValue.StringValue -> {
                if (spec.allowedStringValues != null && value.value !in spec.allowedStringValues) {
                    Resolution.Resolved(
                        ControlValue.StringValue(spec.allowedStringValues.first()),
                        wasClamped = true
                    )
                } else {
                    Resolution.Resolved(value)
                }
            }
            is ControlValue.BoolValue -> Resolution.Resolved(value)
            is ControlValue.IntRangeValue -> Resolution.Resolved(value)
        }
    }
}
