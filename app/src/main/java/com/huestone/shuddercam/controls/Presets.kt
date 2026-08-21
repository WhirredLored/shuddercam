package com.huestone.shuddercam.controls

/**
 * Built-in preset constants (C12 will populate these).
 * For now, just the DEVICE_AUTO baseline preset.
 */
object Presets {
    /** Device-auto: no manual values set; everything is null → device defaults. */
    val DEVICE_AUTO = Preset(
        id = "device_auto",
        displayName = "Auto",
        values = emptyMap()
    )
}
