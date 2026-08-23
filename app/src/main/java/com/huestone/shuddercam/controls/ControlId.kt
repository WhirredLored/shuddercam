package com.huestone.shuddercam.controls

/**
 * Enum of every controllable camera function the app exposes.
 * This is the canonical list — UI, presets, and ControlApplier
 * all key off these IDs.
 */
enum class ControlId {
    AE_MODE,
    ISO,
    SHUTTER_NS,
    AE_COMPENSATION,
    AE_LOCK,
    AF_MODE,
    FOCUS_DISTANCE,
    AWB_MODE,
    AWB_LOCK,
    COLOR_TEMP_K,
    ZOOM_RATIO,
    TORCH_MODE,
    FLASH_MODE,
    FPS_RANGE,
    STABILIZATION_MODE,
    CAPTURE_FORMAT,
    GRID_OVERLAY
}
