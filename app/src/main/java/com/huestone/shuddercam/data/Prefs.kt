package com.huestone.shuddercam.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.huestone.shuddercam.controls.ControlId
import com.huestone.shuddercam.controls.ControlValue
import com.huestone.shuddercam.controls.Preset
import com.huestone.shuddercam.controls.PresetCodec
import com.huestone.shuddercam.controls.Presets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shudder_prefs")

/**
 * DataStore persistence for ShudderCam.
 * Holds: active preset id, nullable overrides, and future fields.
 */
class Prefs(private val context: Context) {

    companion object {
        private val KEY_ACTIVE_PRESET = stringPreferencesKey("active_preset_id")
        private val KEY_USER_PRESETS = stringSetPreferencesKey("user_presets")
        private val KEY_EDGELESS = booleanPreferencesKey("edgeless")
        private val KEY_TRUESIGHT = booleanPreferencesKey("truesight")
        private val KEY_BRIGHTNESS_ASSIST = booleanPreferencesKey("brightness_assist")

        // Portrait and landscape occlude different slices of the world, so each
        // orientation carries its own empirical factor.
        private val KEY_CALIBRATION_PORTRAIT = floatPreferencesKey("calibration_portrait")
        private val KEY_CALIBRATION_LANDSCAPE = floatPreferencesKey("calibration_landscape")

        // Override keys - typed per control
        private val KEY_AE_MODE = intPreferencesKey("override_ae_mode")
        private val KEY_ISO = intPreferencesKey("override_iso")
        private val KEY_SHUTTER_NS = longPreferencesKey("override_shutter_ns")
        private val KEY_AE_COMPENSATION = intPreferencesKey("override_ae_compensation")
        private val KEY_AE_LOCK = booleanPreferencesKey("override_ae_lock")
        private val KEY_AF_MODE = intPreferencesKey("override_af_mode")
        private val KEY_FOCUS_DISTANCE = floatPreferencesKey("override_focus_distance")
        private val KEY_AWB_MODE = intPreferencesKey("override_awb_mode")
        private val KEY_AWB_LOCK = booleanPreferencesKey("override_awb_lock")
        private val KEY_COLOR_TEMP_K = intPreferencesKey("override_color_temp_k")
        private val KEY_ZOOM_RATIO = floatPreferencesKey("override_zoom_ratio")
        private val KEY_TORCH_MODE = booleanPreferencesKey("override_torch_mode")
        private val KEY_FLASH_MODE = intPreferencesKey("override_flash_mode")
        private val KEY_FPS_RANGE_LOWER = intPreferencesKey("override_fps_range_lower")
        private val KEY_FPS_RANGE_UPPER = intPreferencesKey("override_fps_range_upper")
        private val KEY_STABILIZATION_MODE = intPreferencesKey("override_stabilization_mode")
        private val KEY_CAPTURE_FORMAT = stringPreferencesKey("override_capture_format")
        private val KEY_GRID_OVERLAY = booleanPreferencesKey("override_grid_overlay")
    }

    val activePresetId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PRESET] ?: "device_auto"
    }

    val overrides: Flow<Map<ControlId, ControlValue?>> = context.dataStore.data.map { prefs ->
        val map = mutableMapOf<ControlId, ControlValue?>()

        prefs[KEY_AE_MODE]?.let { map[ControlId.AE_MODE] = ControlValue.IntValue(it) }
        prefs[KEY_ISO]?.let { map[ControlId.ISO] = ControlValue.IntValue(it) }
        prefs[KEY_SHUTTER_NS]?.let { map[ControlId.SHUTTER_NS] = ControlValue.LongValue(it) }
        prefs[KEY_AE_COMPENSATION]?.let { map[ControlId.AE_COMPENSATION] = ControlValue.IntValue(it) }
        prefs[KEY_AE_LOCK]?.let { map[ControlId.AE_LOCK] = ControlValue.BoolValue(it) }
        prefs[KEY_AF_MODE]?.let { map[ControlId.AF_MODE] = ControlValue.IntValue(it) }
        prefs[KEY_FOCUS_DISTANCE]?.let { map[ControlId.FOCUS_DISTANCE] = ControlValue.FloatValue(it) }
        prefs[KEY_AWB_MODE]?.let { map[ControlId.AWB_MODE] = ControlValue.IntValue(it) }
        prefs[KEY_AWB_LOCK]?.let { map[ControlId.AWB_LOCK] = ControlValue.BoolValue(it) }
        prefs[KEY_COLOR_TEMP_K]?.let { map[ControlId.COLOR_TEMP_K] = ControlValue.IntValue(it) }
        prefs[KEY_ZOOM_RATIO]?.let { map[ControlId.ZOOM_RATIO] = ControlValue.FloatValue(it) }
        prefs[KEY_TORCH_MODE]?.let { map[ControlId.TORCH_MODE] = ControlValue.BoolValue(it) }
        prefs[KEY_FLASH_MODE]?.let { map[ControlId.FLASH_MODE] = ControlValue.IntValue(it) }
        val fpsLower = prefs[KEY_FPS_RANGE_LOWER]
        val fpsUpper = prefs[KEY_FPS_RANGE_UPPER]
        if (fpsLower != null && fpsUpper != null) {
            map[ControlId.FPS_RANGE] = ControlValue.IntRangeValue(fpsLower, fpsUpper)
        }
        prefs[KEY_STABILIZATION_MODE]?.let { map[ControlId.STABILIZATION_MODE] = ControlValue.IntValue(it) }
        prefs[KEY_CAPTURE_FORMAT]?.let { map[ControlId.CAPTURE_FORMAT] = ControlValue.StringValue(it) }
        prefs[KEY_GRID_OVERLAY]?.let { map[ControlId.GRID_OVERLAY] = ControlValue.BoolValue(it) }

        map
    }

    /** Edgeless survives a cold start — the app may open straight into it. */
    val edgeless: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EDGELESS] ?: false
    }

    suspend fun setEdgeless(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EDGELESS] = enabled
        }
    }

    /** TrueSight implies edgeless — it is edgeless plus a scale calibration. */
    val trueSight: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TRUESIGHT] ?: false
    }

    suspend fun setTrueSight(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TRUESIGHT] = enabled
        }
    }

    /** Off by default: it only ever raises brightness on this app's own window. */
    val brightnessAssist: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BRIGHTNESS_ASSIST] ?: false
    }

    suspend fun setBrightnessAssist(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BRIGHTNESS_ASSIST] = enabled
        }
    }

    /**
     * Calibration factor per orientation. Null means this orientation has never
     * been calibrated, which is what triggers the first-run flow.
     */
    val calibrationPortrait: Flow<Float?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CALIBRATION_PORTRAIT]
    }

    val calibrationLandscape: Flow<Float?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CALIBRATION_LANDSCAPE]
    }

    suspend fun setCalibration(factor: Float, isPortrait: Boolean) {
        context.dataStore.edit { prefs ->
            val key = if (isPortrait) KEY_CALIBRATION_PORTRAIT else KEY_CALIBRATION_LANDSCAPE
            prefs[key] = factor
        }
    }

    /**
     * User-saved presets, a layer entirely separate from the built-in constants.
     * A record this build cannot parse is dropped rather than crashing the flow.
     * Ordered by name so the selector stays stable across restarts.
     */
    val userPresets: Flow<List<Preset>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_USER_PRESETS] ?: emptySet())
            .mapNotNull { PresetCodec.decode(it) }
            .sortedBy { it.displayName.lowercase() }
    }

    /**
     * Saves (or replaces) a user preset. Built-in ids are refused outright so a
     * user preset can never shadow a code constant.
     */
    suspend fun saveUserPreset(preset: Preset) {
        if (Presets.isBuiltIn(preset.id)) return
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_USER_PRESETS] ?: emptySet()
            val withoutSameId = existing.filter { record ->
                PresetCodec.decode(record)?.id != preset.id
            }
            prefs[KEY_USER_PRESETS] = (withoutSameId + PresetCodec.encode(preset)).toSet()
        }
    }

    suspend fun deleteUserPreset(id: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_USER_PRESETS] ?: emptySet()
            prefs[KEY_USER_PRESETS] = existing
                .filter { record -> PresetCodec.decode(record)?.id != id }
                .toSet()
        }
    }

    suspend fun setActivePresetId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PRESET] = id
        }
    }

    suspend fun setOverride(controlId: ControlId, value: ControlValue?) {
        context.dataStore.edit { prefs ->
            if (value == null) {
                clearOverrideKey(prefs, controlId)
                return@edit
            }
            when (controlId) {
                ControlId.AE_MODE -> prefs[KEY_AE_MODE] = (value as ControlValue.IntValue).value
                ControlId.ISO -> prefs[KEY_ISO] = (value as ControlValue.IntValue).value
                ControlId.SHUTTER_NS -> prefs[KEY_SHUTTER_NS] = (value as ControlValue.LongValue).value
                ControlId.AE_COMPENSATION -> prefs[KEY_AE_COMPENSATION] = (value as ControlValue.IntValue).value
                ControlId.AE_LOCK -> prefs[KEY_AE_LOCK] = (value as ControlValue.BoolValue).value
                ControlId.AF_MODE -> prefs[KEY_AF_MODE] = (value as ControlValue.IntValue).value
                ControlId.FOCUS_DISTANCE -> prefs[KEY_FOCUS_DISTANCE] = (value as ControlValue.FloatValue).value
                ControlId.AWB_MODE -> prefs[KEY_AWB_MODE] = (value as ControlValue.IntValue).value
                ControlId.AWB_LOCK -> prefs[KEY_AWB_LOCK] = (value as ControlValue.BoolValue).value
                ControlId.COLOR_TEMP_K -> prefs[KEY_COLOR_TEMP_K] = (value as ControlValue.IntValue).value
                ControlId.ZOOM_RATIO -> prefs[KEY_ZOOM_RATIO] = (value as ControlValue.FloatValue).value
                ControlId.TORCH_MODE -> prefs[KEY_TORCH_MODE] = (value as ControlValue.BoolValue).value
                ControlId.FLASH_MODE -> prefs[KEY_FLASH_MODE] = (value as ControlValue.IntValue).value
                ControlId.FPS_RANGE -> {
                    val range = value as ControlValue.IntRangeValue
                    prefs[KEY_FPS_RANGE_LOWER] = range.lower
                    prefs[KEY_FPS_RANGE_UPPER] = range.upper
                }
                ControlId.STABILIZATION_MODE -> prefs[KEY_STABILIZATION_MODE] = (value as ControlValue.IntValue).value
                ControlId.CAPTURE_FORMAT -> prefs[KEY_CAPTURE_FORMAT] = (value as ControlValue.StringValue).value
                ControlId.GRID_OVERLAY -> prefs[KEY_GRID_OVERLAY] = (value as ControlValue.BoolValue).value
            }
        }
    }

    /**
     * Clears every override at once, returning the camera to the active preset.
     */
    suspend fun clearAllOverrides() {
        context.dataStore.edit { prefs ->
            ControlId.entries.forEach { id -> clearOverrideKey(prefs, id) }
        }
    }

    private fun clearOverrideKey(prefs: androidx.datastore.preferences.core.MutablePreferences, id: ControlId) {
        when (id) {
            ControlId.AE_MODE -> prefs.remove(KEY_AE_MODE)
            ControlId.ISO -> prefs.remove(KEY_ISO)
            ControlId.SHUTTER_NS -> prefs.remove(KEY_SHUTTER_NS)
            ControlId.AE_COMPENSATION -> prefs.remove(KEY_AE_COMPENSATION)
            ControlId.AE_LOCK -> prefs.remove(KEY_AE_LOCK)
            ControlId.AF_MODE -> prefs.remove(KEY_AF_MODE)
            ControlId.FOCUS_DISTANCE -> prefs.remove(KEY_FOCUS_DISTANCE)
            ControlId.AWB_MODE -> prefs.remove(KEY_AWB_MODE)
            ControlId.AWB_LOCK -> prefs.remove(KEY_AWB_LOCK)
            ControlId.COLOR_TEMP_K -> prefs.remove(KEY_COLOR_TEMP_K)
            ControlId.ZOOM_RATIO -> prefs.remove(KEY_ZOOM_RATIO)
            ControlId.TORCH_MODE -> prefs.remove(KEY_TORCH_MODE)
            ControlId.FLASH_MODE -> prefs.remove(KEY_FLASH_MODE)
            ControlId.FPS_RANGE -> {
                prefs.remove(KEY_FPS_RANGE_LOWER)
                prefs.remove(KEY_FPS_RANGE_UPPER)
            }
            ControlId.STABILIZATION_MODE -> prefs.remove(KEY_STABILIZATION_MODE)
            ControlId.CAPTURE_FORMAT -> prefs.remove(KEY_CAPTURE_FORMAT)
            ControlId.GRID_OVERLAY -> prefs.remove(KEY_GRID_OVERLAY)
        }
    }
}
