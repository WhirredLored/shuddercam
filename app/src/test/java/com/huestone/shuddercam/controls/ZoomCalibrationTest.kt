package com.huestone.shuddercam.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for TrueSight's scale calibration. The composition
 * `effectiveZoom = clamp(userZoom * calibration)` lives in the resolution layer,
 * so it is testable without a device — and provably in one place only.
 */
class ZoomCalibrationTest {

    private lateinit var specs: Map<ControlId, ControlSpec>

    private val caps = CapsMirror(
        sensitivityRange = 50..3200,
        exposureTimeRange = 1000L..1_000_000_000L,
        aeCompensationRange = -4..4,
        aeCompensationStep = 0.5f,
        availableAeModes = listOf(0, 1),
        aeLockAvailable = true,
        minimumFocusDistance = 10.0f,
        availableAfModes = listOf(0, 1, 4),
        availableAwbModes = listOf(0, 1, 5),
        awbLockAvailable = true,
        zoomRatioRange = 1.0f..8.0f,
        flashAvailable = true,
        supportedFpsRanges = listOf(15 to 30, 30 to 30),
        availableVideoStabilizationModes = listOf(0, 1),
        availableOpticalStabilizationModes = listOf(0, 1),
        hasRawCapability = false
    )

    @Before
    fun setup() {
        specs = ControlSpec.deriveAll(caps)
    }

    private fun zoomWith(
        calibration: Float,
        overrides: Map<ControlId, ControlValue?> = emptyMap(),
        preset: Preset = Presets.DEVICE_AUTO
    ): EffectiveSettings.Resolution.Resolved =
        EffectiveSettings.resolve(
            ControlId.ZOOM_RATIO, specs, preset, overrides, calibration
        ) as EffectiveSettings.Resolution.Resolved

    @Test
    fun `no calibration leaves zoom exactly as the user set it`() {
        val resolved = zoomWith(
            EffectiveSettings.NO_CALIBRATION,
            mapOf(ControlId.ZOOM_RATIO to ControlValue.FloatValue(2.0f))
        )

        assertEquals(ControlValue.FloatValue(2.0f), resolved.value)
    }

    @Test
    fun `calibration applies at rest, with no user zoom of their own`() {
        val resolved = zoomWith(1.8f)

        // userZoom defaults to 1.0, so the calibration factor is the whole story.
        assertEquals(ControlValue.FloatValue(1.8f), resolved.value)
    }

    @Test
    fun `user pinch rides on top of calibration`() {
        val resolved = zoomWith(
            2.0f,
            mapOf(ControlId.ZOOM_RATIO to ControlValue.FloatValue(1.5f))
        )

        assertEquals(ControlValue.FloatValue(3.0f), resolved.value)
    }

    @Test
    fun `composed zoom clamps to the lens range instead of overshooting`() {
        val resolved = zoomWith(
            3.0f,
            mapOf(ControlId.ZOOM_RATIO to ControlValue.FloatValue(6.0f))
        )

        // 6.0 * 3.0 = 18.0, far past the 8x ceiling.
        assertEquals(ControlValue.FloatValue(8.0f), resolved.value)
        assertTrue(resolved.wasClamped)
    }

    @Test
    fun `clearing the zoom override returns to calibrated one-to-one`() {
        val zoomed = zoomWith(
            1.7f,
            mapOf(ControlId.ZOOM_RATIO to ControlValue.FloatValue(2.0f))
        )
        assertEquals(ControlValue.FloatValue(3.4f), zoomed.value)

        // The reset affordance clears the override, nothing else.
        val reset = zoomWith(1.7f, emptyMap())
        assertEquals(ControlValue.FloatValue(1.7f), reset.value)
    }

    @Test
    fun `a preset zoom composes the same way an override does`() {
        val preset = Preset(
            id = "zoomy",
            displayName = "Zoomy",
            description = "test",
            values = mapOf(ControlId.ZOOM_RATIO to ControlValue.FloatValue(2.0f))
        )

        assertEquals(ControlValue.FloatValue(4.0f), zoomWith(2.0f, emptyMap(), preset).value)
    }

    @Test
    fun `calibration never leaks into any other control`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ISO to ControlValue.IntValue(800),
            ControlId.FOCUS_DISTANCE to ControlValue.FloatValue(2.0f)
        )

        val resolved = EffectiveSettings.resolveAll(specs, Presets.DEVICE_AUTO, overrides, 2.5f)

        assertEquals(
            ControlValue.IntValue(800),
            (resolved[ControlId.ISO] as EffectiveSettings.Resolution.Resolved).value
        )
        assertEquals(
            ControlValue.FloatValue(2.0f),
            (resolved[ControlId.FOCUS_DISTANCE] as EffectiveSettings.Resolution.Resolved).value
        )
    }

    @Test
    fun `zoom is still absent on a camera that cannot zoom`() {
        val fixedSpecs = ControlSpec.deriveAll(caps.copy(zoomRatioRange = null))

        val resolved = EffectiveSettings.resolve(
            ControlId.ZOOM_RATIO, fixedSpecs, Presets.DEVICE_AUTO, emptyMap(), 2.0f
        )

        assertTrue(resolved is EffectiveSettings.Resolution.Absent)
    }

    @Test
    fun `userZoom reads back what the slider and pinch must edit`() {
        assertEquals(
            1.0f,
            EffectiveSettings.userZoom(emptyMap(), Presets.DEVICE_AUTO),
            0.0001f
        )
        assertEquals(
            2.5f,
            EffectiveSettings.userZoom(
                mapOf(ControlId.ZOOM_RATIO to ControlValue.FloatValue(2.5f)),
                Presets.DEVICE_AUTO
            ),
            0.0001f
        )
    }

    @Test
    fun `userZoom ignores calibration entirely so editing cannot compound it`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ZOOM_RATIO to ControlValue.FloatValue(1.5f)
        )

        // The effective value is 3.0 under a 2x calibration...
        assertEquals(ControlValue.FloatValue(3.0f), zoomWith(2.0f, overrides).value)
        // ...but what the UI edits stays 1.5, so a re-save writes 1.5 back.
        assertEquals(1.5f, EffectiveSettings.userZoom(overrides, Presets.DEVICE_AUTO), 0.0001f)
    }

    @Test
    fun `the calibration range covers the documented one to three`() {
        assertEquals(1.0f, EffectiveSettings.CALIBRATION_RANGE.start, 0.0001f)
        assertEquals(3.0f, EffectiveSettings.CALIBRATION_RANGE.endInclusive, 0.0001f)
    }
}
