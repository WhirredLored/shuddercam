package com.huestone.shuddercam.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for EffectiveSettings resolution and clamping logic.
 * Covers: no overrides, some overrides, all overrides, preset switch
 * with overrides held, clamping of out-of-range overrides, and
 * unsupported controls resolving to Absent.
 */
class EffectiveSettingsTest {

    private lateinit var fullCaps: CapsMirror
    private lateinit var specs: Map<ControlId, ControlSpec>

    @Before
    fun setup() {
        fullCaps = CapsMirror(
            sensitivityRange = 50..3200,
            exposureTimeRange = 1000L..1_000_000_000L,
            aeCompensationRange = -4..4,
            aeCompensationStep = 0.5f,
            availableAeModes = listOf(0, 1, 2), // OFF, ON, ON_AUTO_FLASH
            aeLockAvailable = true,
            minimumFocusDistance = 10.0f,
            availableAfModes = listOf(0, 1, 4), // OFF, AUTO, CONTINUOUS_PICTURE
            availableAwbModes = listOf(0, 1, 2, 3), // OFF, AUTO, INCANDESCENT, FLUORESCENT
            awbLockAvailable = true,
            zoomRatioRange = 1.0f..8.0f,
            flashAvailable = true,
            supportedFpsRanges = listOf(15 to 30, 30 to 30),
            availableVideoStabilizationModes = listOf(0, 1),
            availableOpticalStabilizationModes = listOf(0, 1),
            hasRawCapability = true
        )
        specs = ControlSpec.deriveAll(fullCaps)
    }

    @Test
    fun `no overrides - device auto preset - resolves to device defaults`() {
        val result = EffectiveSettings.resolveAll(
            specs = specs,
            activePreset = Presets.DEVICE_AUTO,
            overrides = emptyMap()
        )

        // ISO should resolve to auto (0)
        val isoRes = result[ControlId.ISO]
        assertTrue(isoRes is EffectiveSettings.Resolution.Resolved)
        val isoValue = (isoRes as EffectiveSettings.Resolution.Resolved).value
        assertEquals(ControlValue.IntValue(0), isoValue)
        assertFalse(isoRes.wasClamped)
    }

    @Test
    fun `some overrides - override wins over preset`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ISO to ControlValue.IntValue(800)
        )

        val result = EffectiveSettings.resolve(
            id = ControlId.ISO,
            specs = specs,
            activePreset = Presets.DEVICE_AUTO,
            overrides = overrides
        )

        assertTrue(result is EffectiveSettings.Resolution.Resolved)
        assertEquals(
            ControlValue.IntValue(800),
            (result as EffectiveSettings.Resolution.Resolved).value
        )
        assertFalse(result.wasClamped)
    }

    @Test
    fun `all overrides - every control resolves to its override`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ISO to ControlValue.IntValue(1600),
            ControlId.SHUTTER_NS to ControlValue.LongValue(100_000_000L),
            ControlId.AE_COMPENSATION to ControlValue.IntValue(2),
            ControlId.ZOOM_RATIO to ControlValue.FloatValue(2.5f),
            ControlId.FOCUS_DISTANCE to ControlValue.FloatValue(5.0f)
        )

        val result = EffectiveSettings.resolveAll(specs, Presets.DEVICE_AUTO, overrides)

        assertEquals(
            ControlValue.IntValue(1600),
            (result[ControlId.ISO] as EffectiveSettings.Resolution.Resolved).value
        )
        assertEquals(
            ControlValue.LongValue(100_000_000L),
            (result[ControlId.SHUTTER_NS] as EffectiveSettings.Resolution.Resolved).value
        )
        assertEquals(
            ControlValue.FloatValue(2.5f),
            (result[ControlId.ZOOM_RATIO] as EffectiveSettings.Resolution.Resolved).value
        )
    }

    @Test
    fun `preset values used when no override set`() {
        val preset = Preset(
            id = "night",
            displayName = "Night",
            values = mapOf(
                ControlId.ISO to ControlValue.IntValue(1600),
                ControlId.SHUTTER_NS to ControlValue.LongValue(500_000_000L)
            )
        )

        val result = EffectiveSettings.resolve(
            ControlId.ISO, specs, preset, emptyMap()
        )

        assertTrue(result is EffectiveSettings.Resolution.Resolved)
        assertEquals(
            ControlValue.IntValue(1600),
            (result as EffectiveSettings.Resolution.Resolved).value
        )
    }

    @Test
    fun `preset switch with overrides held - overrides still win`() {
        val preset1 = Preset(
            id = "p1", displayName = "P1",
            values = mapOf(ControlId.ISO to ControlValue.IntValue(400))
        )
        val preset2 = Preset(
            id = "p2", displayName = "P2",
            values = mapOf(ControlId.ISO to ControlValue.IntValue(3200))
        )
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ISO to ControlValue.IntValue(800)
        )

        val res1 = EffectiveSettings.resolve(ControlId.ISO, specs, preset1, overrides)
        val res2 = EffectiveSettings.resolve(ControlId.ISO, specs, preset2, overrides)

        // Both should resolve to the override value 800, not the preset values
        assertEquals(
            ControlValue.IntValue(800),
            (res1 as EffectiveSettings.Resolution.Resolved).value
        )
        assertEquals(
            ControlValue.IntValue(800),
            (res2 as EffectiveSettings.Resolution.Resolved).value
        )
    }

    @Test
    fun `clamping - out-of-range ISO gets clamped`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ISO to ControlValue.IntValue(99999) // Way above 3200 max
        )

        val result = EffectiveSettings.resolve(
            ControlId.ISO, specs, Presets.DEVICE_AUTO, overrides
        )

        assertTrue(result is EffectiveSettings.Resolution.Resolved)
        val resolved = result as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntValue(3200), resolved.value)
        assertTrue(resolved.wasClamped)
    }

    @Test
    fun `clamping - out-of-range zoom gets clamped to range`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ZOOM_RATIO to ControlValue.FloatValue(100.0f) // Way above 8x max
        )

        val result = EffectiveSettings.resolve(
            ControlId.ZOOM_RATIO, specs, Presets.DEVICE_AUTO, overrides
        )

        assertTrue(result is EffectiveSettings.Resolution.Resolved)
        val resolved = result as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.FloatValue(8.0f), resolved.value)
        assertTrue(resolved.wasClamped)
    }

    @Test
    fun `clamping - in-range value not clamped`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ISO to ControlValue.IntValue(800) // Within 50..3200
        )

        val result = EffectiveSettings.resolve(
            ControlId.ISO, specs, Presets.DEVICE_AUTO, overrides
        )

        assertTrue(result is EffectiveSettings.Resolution.Resolved)
        val resolved = result as EffectiveSettings.Resolution.Resolved
        assertFalse(resolved.wasClamped)
    }

    @Test
    fun `unsupported control resolves to Absent`() {
        // Create caps with no sensitivity range (no ISO support)
        val limitedCaps = fullCaps.copy(sensitivityRange = null)
        val limitedSpecs = ControlSpec.deriveAll(limitedCaps)

        val result = EffectiveSettings.resolve(
            ControlId.ISO, limitedSpecs, Presets.DEVICE_AUTO, emptyMap()
        )

        assertTrue(result is EffectiveSettings.Resolution.Absent)
    }

    @Test
    fun `unsupported focus distance resolves to Absent on fixed-focus`() {
        val fixedFocusCaps = fullCaps.copy(minimumFocusDistance = 0f)
        val fixedFocusSpecs = ControlSpec.deriveAll(fixedFocusCaps)

        val result = EffectiveSettings.resolve(
            ControlId.FOCUS_DISTANCE, fixedFocusSpecs, Presets.DEVICE_AUTO, emptyMap()
        )

        assertTrue(result is EffectiveSettings.Resolution.Absent)
    }

    @Test
    fun `grid overlay always available`() {
        val result = EffectiveSettings.resolve(
            ControlId.GRID_OVERLAY, specs, Presets.DEVICE_AUTO, emptyMap()
        )

        assertTrue(result is EffectiveSettings.Resolution.Resolved)
    }

    @Test
    fun `capture format clamps invalid string to first allowed`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.CAPTURE_FORMAT to ControlValue.StringValue("TIFF") // Not supported
        )

        val result = EffectiveSettings.resolve(
            ControlId.CAPTURE_FORMAT, specs, Presets.DEVICE_AUTO, overrides
        )

        assertTrue(result is EffectiveSettings.Resolution.Resolved)
        val resolved = result as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.StringValue("JPEG"), resolved.value)
        assertTrue(resolved.wasClamped)
    }

    @Test
    fun `fps range override matching a probed option is not clamped`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.FPS_RANGE to ControlValue.IntRangeValue(30, 30)
        )

        val result = EffectiveSettings.resolve(
            ControlId.FPS_RANGE, specs, Presets.DEVICE_AUTO, overrides
        )

        val resolved = result as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntRangeValue(30, 30), resolved.value)
        assertFalse(resolved.wasClamped)
    }

    @Test
    fun `fps range override outside probed options snaps to nearest`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            // 60fps device migrated onto a 30fps-max camera
            ControlId.FPS_RANGE to ControlValue.IntRangeValue(60, 60)
        )

        val result = EffectiveSettings.resolve(
            ControlId.FPS_RANGE, specs, Presets.DEVICE_AUTO, overrides
        )

        val resolved = result as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntRangeValue(30, 30), resolved.value)
        assertTrue(resolved.wasClamped)
    }

    @Test
    fun `fps device auto snaps into the probed options`() {
        // Device offers only 24 and 60 fps; the 15-30 default matches neither.
        val caps = fullCaps.copy(supportedFpsRanges = listOf(24 to 24, 60 to 60))
        val narrowSpecs = ControlSpec.deriveAll(caps)

        val result = EffectiveSettings.resolve(
            ControlId.FPS_RANGE, narrowSpecs, Presets.DEVICE_AUTO, emptyMap()
        )

        val resolved = result as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntRangeValue(24, 24), resolved.value)
    }

    @Test
    fun `fps range absent when device reports no ranges`() {
        val caps = fullCaps.copy(supportedFpsRanges = emptyList())
        val noFpsSpecs = ControlSpec.deriveAll(caps)

        val result = EffectiveSettings.resolve(
            ControlId.FPS_RANGE, noFpsSpecs, Presets.DEVICE_AUTO, emptyMap()
        )

        assertTrue(result is EffectiveSettings.Resolution.Absent)
    }

    @Test
    fun `stabilization exposes only probed modes`() {
        val spec = specs[ControlId.STABILIZATION_MODE]!!
        assertTrue(spec.available)
        assertEquals(listOf(0, 1), spec.allowedIntValues)
    }

    @Test
    fun `stabilization absent when device offers a single mode`() {
        val caps = fullCaps.copy(availableVideoStabilizationModes = listOf(0))
        val noStabSpecs = ControlSpec.deriveAll(caps)

        val result = EffectiveSettings.resolve(
            ControlId.STABILIZATION_MODE, noStabSpecs, Presets.DEVICE_AUTO, emptyMap()
        )

        assertTrue(result is EffectiveSettings.Resolution.Absent)
    }

    @Test
    fun `stabilization override outside probed modes snaps to nearest`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.STABILIZATION_MODE to ControlValue.IntValue(2) // PREVIEW, unsupported here
        )

        val result = EffectiveSettings.resolve(
            ControlId.STABILIZATION_MODE, specs, Presets.DEVICE_AUTO, overrides
        )

        val resolved = result as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntValue(1), resolved.value)
        assertTrue(resolved.wasClamped)
    }

    @Test
    fun `torch and zoom absent on hardware that lacks them`() {
        val caps = fullCaps.copy(flashAvailable = false, zoomRatioRange = null)
        val bareSpecs = ControlSpec.deriveAll(caps)

        assertTrue(
            EffectiveSettings.resolve(
                ControlId.TORCH_MODE, bareSpecs, Presets.DEVICE_AUTO, emptyMap()
            ) is EffectiveSettings.Resolution.Absent
        )
        assertTrue(
            EffectiveSettings.resolve(
                ControlId.ZOOM_RATIO, bareSpecs, Presets.DEVICE_AUTO, emptyMap()
            ) is EffectiveSettings.Resolution.Absent
        )
    }

    @Test
    fun `clamping - below-range shutter speed gets clamped up`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.SHUTTER_NS to ControlValue.LongValue(1L) // Below 1000 min
        )

        val result = EffectiveSettings.resolve(
            ControlId.SHUTTER_NS, specs, Presets.DEVICE_AUTO, overrides
        )

        assertTrue(result is EffectiveSettings.Resolution.Resolved)
        val resolved = result as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.LongValue(1000L), resolved.value)
        assertTrue(resolved.wasClamped)
    }
}
