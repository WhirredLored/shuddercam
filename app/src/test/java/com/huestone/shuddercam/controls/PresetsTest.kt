package com.huestone.shuddercam.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for the built-in presets: every preset must resolve cleanly on
 * both a FULL device and a stripped-down LIMITED one, with clamping (Law 5)
 * adapting values and unsupported entries dropping out to Absent.
 */
class PresetsTest {

    private lateinit var fullSpecs: Map<ControlId, ControlSpec>
    private lateinit var limitedSpecs: Map<ControlId, ControlSpec>

    /** A FULL-level device: manual everything, wide ranges. */
    private val fullCaps = CapsMirror(
        sensitivityRange = 50..12800,
        exposureTimeRange = 1000L..2_000_000_000L,
        aeCompensationRange = -12..12,
        aeCompensationStep = 0.5f,
        availableAeModes = listOf(0, 1, 2),
        aeLockAvailable = true,
        minimumFocusDistance = 10.0f,
        availableAfModes = listOf(0, 1, 4),
        availableAwbModes = listOf(0, 1, 2, 3, 5, 6),
        awbLockAvailable = true,
        zoomRatioRange = 1.0f..10.0f,
        flashAvailable = true,
        supportedFpsRanges = listOf(15 to 30, 30 to 30, 60 to 60),
        availableVideoStabilizationModes = listOf(0, 1),
        availableOpticalStabilizationModes = listOf(0, 1),
        hasRawCapability = true
    )

    /**
     * A LIMITED device: no manual exposure, no manual focus, no manual WB,
     * no stabilization choice, no flash, one frame rate.
     */
    private val limitedCaps = CapsMirror(
        sensitivityRange = null,
        exposureTimeRange = null,
        aeCompensationRange = -2..2,
        aeCompensationStep = 1.0f,
        availableAeModes = listOf(1),
        aeLockAvailable = false,
        minimumFocusDistance = 0f,
        availableAfModes = listOf(1, 4),
        availableAwbModes = listOf(1),
        awbLockAvailable = false,
        zoomRatioRange = null,
        flashAvailable = false,
        supportedFpsRanges = listOf(15 to 30),
        availableVideoStabilizationModes = listOf(0),
        availableOpticalStabilizationModes = listOf(0),
        hasRawCapability = false
    )

    @Before
    fun setup() {
        fullSpecs = ControlSpec.deriveAll(fullCaps)
        limitedSpecs = ControlSpec.deriveAll(limitedCaps)
    }

    @Test
    fun `five built-in presets ship, device auto first`() {
        assertEquals(5, Presets.ALL.size)
        assertEquals(Presets.DEVICE_AUTO, Presets.ALL.first())
        assertEquals(Presets.ALL.size, Presets.ALL.map { it.id }.distinct().size)
    }

    @Test
    fun `every preset carries a description for the UI`() {
        Presets.ALL.forEach { preset ->
            assertTrue(
                "${preset.id} needs a description",
                preset.description.isNotBlank()
            )
        }
    }

    @Test
    fun `uv hunt is described for 365nm work`() {
        assertEquals("for 365nm fluorescence work", Presets.UV_HUNT.description)
    }

    @Test
    fun `every preset resolves cleanly on a FULL device`() {
        Presets.ALL.forEach { preset ->
            val resolved = EffectiveSettings.resolveAll(fullSpecs, preset, emptyMap())
            // Every control resolves to something — never a crash, never a null.
            ControlId.entries.forEach { id ->
                assertNotNull("${preset.id}/$id", resolved[id])
            }
        }
    }

    @Test
    fun `every preset resolves cleanly on a LIMITED device`() {
        Presets.ALL.forEach { preset ->
            val resolved = EffectiveSettings.resolveAll(limitedSpecs, preset, emptyMap())
            ControlId.entries.forEach { id ->
                assertNotNull("${preset.id}/$id", resolved[id])
            }
        }
    }

    @Test
    fun `preset entries for unsupported controls are ignored`() {
        // LONG EXPOSURE asks for manual ISO, shutter and focus distance; the
        // LIMITED device offers none of them.
        val resolved = EffectiveSettings.resolveAll(limitedSpecs, Presets.LONG_EXPOSURE, emptyMap())

        assertTrue(resolved[ControlId.ISO] is EffectiveSettings.Resolution.Absent)
        assertTrue(resolved[ControlId.SHUTTER_NS] is EffectiveSettings.Resolution.Absent)
        assertTrue(resolved[ControlId.FOCUS_DISTANCE] is EffectiveSettings.Resolution.Absent)
    }

    @Test
    fun `long exposure holds a one second shutter at base ISO on a FULL device`() {
        val resolved = EffectiveSettings.resolveAll(fullSpecs, Presets.LONG_EXPOSURE, emptyMap())

        assertEquals(
            ControlValue.LongValue(1_000_000_000L),
            (resolved[ControlId.SHUTTER_NS] as EffectiveSettings.Resolution.Resolved).value
        )
        // ISO 1 clamps up to the sensor floor rather than staying at an invalid 1.
        val iso = resolved[ControlId.ISO] as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntValue(50), iso.value)
        assertTrue(iso.wasClamped)
    }

    @Test
    fun `uv hunt ISO clamps down on a low max ISO device`() {
        val lowIsoSpecs = ControlSpec.deriveAll(fullCaps.copy(sensitivityRange = 100..800))

        val resolved = EffectiveSettings.resolve(
            ControlId.ISO, lowIsoSpecs, Presets.UV_HUNT, emptyMap()
        )

        assertTrue(resolved is EffectiveSettings.Resolution.Resolved)
        val iso = resolved as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntValue(800), iso.value)
        assertTrue(iso.wasClamped)
    }

    @Test
    fun `uv hunt keeps its full ISO on a wide range sensor`() {
        val iso = EffectiveSettings.resolve(
            ControlId.ISO, fullSpecs, Presets.UV_HUNT, emptyMap()
        ) as EffectiveSettings.Resolution.Resolved

        assertEquals(ControlValue.IntValue(6400), iso.value)
        assertTrue(!iso.wasClamped)
    }

    @Test
    fun `uv hunt falls back to auto white balance where manual WB is unsupported`() {
        val awb = EffectiveSettings.resolve(
            ControlId.AWB_MODE, limitedSpecs, Presets.UV_HUNT, emptyMap()
        ) as EffectiveSettings.Resolution.Resolved

        // AWB_MODE_OFF is not offered, so the nearest supported mode wins.
        assertEquals(ControlValue.IntValue(1), awb.value)
        assertTrue(awb.wasClamped)
        // And the Kelvin control itself is simply absent.
        assertTrue(
            EffectiveSettings.resolve(
                ControlId.COLOR_TEMP_K, limitedSpecs, Presets.UV_HUNT, emptyMap()
            ) is EffectiveSettings.Resolution.Absent
        )
    }

    @Test
    fun `daylight crisp fastest frame rate clamps to what the device offers`() {
        val onFull = EffectiveSettings.resolve(
            ControlId.FPS_RANGE, fullSpecs, Presets.DAYLIGHT_CRISP, emptyMap()
        ) as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntRangeValue(60, 60), onFull.value)

        val onLimited = EffectiveSettings.resolve(
            ControlId.FPS_RANGE, limitedSpecs, Presets.DAYLIGHT_CRISP, emptyMap()
        ) as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntRangeValue(15, 30), onLimited.value)
        assertTrue(onLimited.wasClamped)
    }

    @Test
    fun `night handheld asks for stabilization and negative EV`() {
        val resolved = EffectiveSettings.resolveAll(fullSpecs, Presets.NIGHT_HANDHELD, emptyMap())

        assertEquals(
            ControlValue.IntValue(-2),
            (resolved[ControlId.AE_COMPENSATION] as EffectiveSettings.Resolution.Resolved).value
        )
        assertEquals(
            ControlValue.IntValue(1),
            (resolved[ControlId.STABILIZATION_MODE] as EffectiveSettings.Resolution.Resolved).value
        )
    }

    @Test
    fun `night handheld EV clamps into a narrow compensation range`() {
        // The LIMITED device only allows -2..2, which happens to fit; a tighter
        // device must still land inside its own range.
        val narrowSpecs = ControlSpec.deriveAll(limitedCaps.copy(aeCompensationRange = -1..1))

        val ev = EffectiveSettings.resolve(
            ControlId.AE_COMPENSATION, narrowSpecs, Presets.NIGHT_HANDHELD, emptyMap()
        ) as EffectiveSettings.Resolution.Resolved

        assertEquals(ControlValue.IntValue(-1), ev.value)
        assertTrue(ev.wasClamped)
    }

    @Test
    fun `overrides shadow the preset beneath them`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ISO to ControlValue.IntValue(400)
        )

        val resolved = EffectiveSettings.resolve(
            ControlId.ISO, fullSpecs, Presets.UV_HUNT, overrides
        ) as EffectiveSettings.Resolution.Resolved

        assertEquals(ControlValue.IntValue(400), resolved.value)
    }

    @Test
    fun `unknown persisted preset id falls back to device auto`() {
        assertEquals(Presets.DEVICE_AUTO, Presets.byId("preset_from_a_future_build"))
        assertEquals(Presets.UV_HUNT, Presets.byId("uv_hunt"))
    }
}
