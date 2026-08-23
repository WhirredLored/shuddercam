package com.huestone.shuddercam.controls

/**
 * A Preset holds a full or partial assignment of values for ControlIds.
 * Built-in presets are immutable `val` constants (Law 2).
 * Uncovered ids fall back to DEVICE_AUTO behavior in EffectiveSettings.
 */
data class Preset(
    val id: String,
    val displayName: String,
    val description: String,
    val values: Map<ControlId, ControlValue>
) {
    /**
     * Returns the preset value for the given control, or null if uncovered.
     */
    fun value(controlId: ControlId): ControlValue? = values[controlId]
}
