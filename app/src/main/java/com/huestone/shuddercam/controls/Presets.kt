package com.huestone.shuddercam.controls

/**
 * Built-in presets: immutable `val` constants (Law 2). Nothing writes to them —
 * user changes live as nullable overrides layered on top.
 *
 * Values are deliberately expressed as plain ControlValues rather than
 * device-specific numbers. Clamping (Law 5) adapts them per device: ISO 1 means
 * "this camera's floor", ISO 6400 means "its ceiling", and a preset entry for a
 * control the device lacks resolves to Absent and is simply ignored.
 */
object Presets {

    /** Device-auto: no manual values set; everything falls through to device defaults. */
    val DEVICE_AUTO = Preset(
        id = "device_auto",
        displayName = "Device Auto",
        description = "Everything left to the camera's own automatic behaviour",
        values = emptyMap()
    )

    val NIGHT_HANDHELD = Preset(
        id = "night_handheld",
        displayName = "Night Hand-held",
        description = "Auto exposure pulled down, steadied, at the fastest steady frame rate",
        values = mapOf(
            ControlId.AE_MODE to ControlValue.IntValue(1), // AE on
            ControlId.AE_COMPENSATION to ControlValue.IntValue(-2),
            // Clamps to the nearest range this camera actually offers.
            ControlId.FPS_RANGE to ControlValue.IntRangeValue(30, 30),
            ControlId.STABILIZATION_MODE to ControlValue.IntValue(1)
        )
    )

    val LONG_EXPOSURE = Preset(
        id = "long_exposure",
        displayName = "Long Exposure",
        description = "One-second shutter at base ISO, focused at infinity",
        values = mapOf(
            ControlId.AE_MODE to ControlValue.IntValue(0), // AE off
            ControlId.SHUTTER_NS to ControlValue.LongValue(1_000_000_000L),
            ControlId.ISO to ControlValue.IntValue(1), // clamps up to the sensor's minimum
            ControlId.AF_MODE to ControlValue.IntValue(0), // manual focus
            ControlId.FOCUS_DISTANCE to ControlValue.FloatValue(0f), // infinity
            ControlId.TORCH_MODE to ControlValue.BoolValue(false)
        )
    )

    val DAYLIGHT_CRISP = Preset(
        id = "daylight_crisp",
        displayName = "Daylight Crisp",
        description = "Daylight balance, neutral exposure, biased to the fastest shutter",
        values = mapOf(
            ControlId.AE_MODE to ControlValue.IntValue(1), // AE on
            ControlId.AE_COMPENSATION to ControlValue.IntValue(0),
            ControlId.AWB_MODE to ControlValue.IntValue(5), // daylight
            ControlId.STABILIZATION_MODE to ControlValue.IntValue(1),
            // A high fixed frame rate caps how long any single exposure can run,
            // which is the only shutter bias the auto-exposure path exposes.
            ControlId.FPS_RANGE to ControlValue.IntRangeValue(60, 60)
        )
    )

    val UV_HUNT = Preset(
        id = "uv_hunt",
        displayName = "UV Hunt",
        description = "for 365nm fluorescence work",
        values = mapOf(
            ControlId.AE_MODE to ControlValue.IntValue(0), // AE off
            ControlId.ISO to ControlValue.IntValue(6400), // clamps down to the sensor's ceiling
            ControlId.SHUTTER_NS to ControlValue.LongValue(66_666_667L), // ~1/15s
            ControlId.AWB_MODE to ControlValue.IntValue(0), // manual WB where supported
            ControlId.COLOR_TEMP_K to ControlValue.IntValue(4500),
            ControlId.TORCH_MODE to ControlValue.BoolValue(false)
        )
    )

    /** Every built-in preset, in selector order. */
    val ALL = listOf(DEVICE_AUTO, NIGHT_HANDHELD, LONG_EXPOSURE, DAYLIGHT_CRISP, UV_HUNT)

    /**
     * Resolves a persisted preset id against the built-ins plus any user-saved
     * presets, falling back to DEVICE_AUTO for an id that no longer exists —
     * which is exactly what happens when the active user preset is deleted.
     */
    fun byId(id: String, userPresets: List<Preset> = emptyList()): Preset =
        ALL.firstOrNull { it.id == id }
            ?: userPresets.firstOrNull { it.id == id }
            ?: DEVICE_AUTO

    /** Built-ins are code constants; only user presets can be renamed or deleted. */
    fun isBuiltIn(id: String): Boolean = ALL.any { it.id == id }
}
