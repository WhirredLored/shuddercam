package com.huestone.shuddercam.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for the thermal governor's policy. The tiers are pure Kotlin so
 * every throttling decision is testable without heating a real phone.
 */
class ThermalTierTest {

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
        supportedFpsRanges = listOf(15 to 30, 30 to 30, 60 to 60),
        availableVideoStabilizationModes = listOf(0, 1),
        availableOpticalStabilizationModes = listOf(0, 1),
        hasRawCapability = false
    )

    private val available = listOf(15 to 30, 30 to 30, 60 to 60)

    @Before
    fun setup() {
        specs = ControlSpec.deriveAll(caps)
    }

    @Test
    fun `normal tier allows everything`() {
        assertTrue(ThermalTier.NORMAL.allowsTorch)
        assertTrue(ThermalTier.NORMAL.allowsFocusLoupe)
        assertNull(ThermalTier.NORMAL.fpsCeiling)
    }

    @Test
    fun `moderate suspends the loupe but keeps the torch`() {
        assertTrue(ThermalTier.MODERATE.allowsTorch)
        assertFalse(ThermalTier.MODERATE.allowsFocusLoupe)
        assertEquals(30, ThermalTier.MODERATE.fpsCeiling)
    }

    @Test
    fun `severe disables the torch`() {
        assertFalse(ThermalTier.SEVERE.allowsTorch)
        assertFalse(ThermalTier.SEVERE.allowsFocusLoupe)
    }

    @Test
    fun `normal never rewrites the frame rate`() {
        assertNull(
            ThermalTier.governFpsRange(
                ThermalTier.NORMAL, ControlValue.IntRangeValue(60, 60), available
            )
        )
    }

    @Test
    fun `moderate caps a 60fps request to the best option at or below 30`() {
        val governed = ThermalTier.governFpsRange(
            ThermalTier.MODERATE, ControlValue.IntRangeValue(60, 60), available
        )

        // 15-30 and 30-30 both sit at the ceiling; the range wins because auto
        // exposure can drop below 30 inside it, which a fixed 30 cannot do.
        assertEquals(ControlValue.IntRangeValue(15, 30), governed)
    }

    @Test
    fun `moderate leaves a request already within the cap alone`() {
        assertNull(
            ThermalTier.governFpsRange(
                ThermalTier.MODERATE, ControlValue.IntRangeValue(15, 30), available
            )
        )
    }

    @Test
    fun `severe drops to the coolest range the device reports`() {
        val governed = ThermalTier.governFpsRange(
            ThermalTier.SEVERE, ControlValue.IntRangeValue(60, 60), available
        )

        assertEquals(ControlValue.IntRangeValue(15, 30), governed)
    }

    @Test
    fun `severe leaves the request alone when it is already the coolest`() {
        assertNull(
            ThermalTier.governFpsRange(
                ThermalTier.SEVERE, ControlValue.IntRangeValue(15, 30), available
            )
        )
    }

    @Test
    fun `a device offering nothing cannot be governed into an invalid range`() {
        assertNull(
            ThermalTier.governFpsRange(
                ThermalTier.SEVERE, ControlValue.IntRangeValue(30, 30), emptyList()
            )
        )
    }

    @Test
    fun `a device with only fast ranges still gets its slowest under moderate`() {
        val fastOnly = listOf(60 to 60, 120 to 120)

        val governed = ThermalTier.governFpsRange(
            ThermalTier.MODERATE, ControlValue.IntRangeValue(120, 120), fastOnly
        )

        assertEquals(ControlValue.IntRangeValue(60, 60), governed)
    }

    @Test
    fun `resolution applies the frame rate cap under moderate`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.FPS_RANGE to ControlValue.IntRangeValue(60, 60)
        )

        val resolved = EffectiveSettings.resolve(
            ControlId.FPS_RANGE, specs, Presets.DEVICE_AUTO, overrides,
            EffectiveSettings.NO_CALIBRATION, ThermalTier.MODERATE
        ) as EffectiveSettings.Resolution.Resolved

        assertEquals(ControlValue.IntRangeValue(15, 30), resolved.value)
        assertTrue(resolved.wasClamped)
    }

    @Test
    fun `resolution forces the torch off under severe`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.TORCH_MODE to ControlValue.BoolValue(true)
        )

        val resolved = EffectiveSettings.resolve(
            ControlId.TORCH_MODE, specs, Presets.DEVICE_AUTO, overrides,
            EffectiveSettings.NO_CALIBRATION, ThermalTier.SEVERE
        ) as EffectiveSettings.Resolution.Resolved

        assertEquals(ControlValue.BoolValue(false), resolved.value)
    }

    @Test
    fun `the torch comes back when the device cools`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.TORCH_MODE to ControlValue.BoolValue(true)
        )

        // The stored override was never touched, so recovery is automatic.
        val recovered = EffectiveSettings.resolve(
            ControlId.TORCH_MODE, specs, Presets.DEVICE_AUTO, overrides,
            EffectiveSettings.NO_CALIBRATION, ThermalTier.NORMAL
        ) as EffectiveSettings.Resolution.Resolved

        assertEquals(ControlValue.BoolValue(true), recovered.value)
    }

    @Test
    fun `throttling leaves every other control untouched`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ISO to ControlValue.IntValue(1600),
            ControlId.ZOOM_RATIO to ControlValue.FloatValue(3.0f)
        )

        val resolved = EffectiveSettings.resolveAll(
            specs, Presets.DEVICE_AUTO, overrides,
            EffectiveSettings.NO_CALIBRATION, ThermalTier.SEVERE
        )

        assertEquals(
            ControlValue.IntValue(1600),
            (resolved[ControlId.ISO] as EffectiveSettings.Resolution.Resolved).value
        )
        assertEquals(
            ControlValue.FloatValue(3.0f),
            (resolved[ControlId.ZOOM_RATIO] as EffectiveSettings.Resolution.Resolved).value
        )
    }

    @Test
    fun `an unsupported control stays absent no matter how hot it gets`() {
        val noFlashSpecs = ControlSpec.deriveAll(caps.copy(flashAvailable = false))

        val resolved = EffectiveSettings.resolve(
            ControlId.TORCH_MODE, noFlashSpecs, Presets.DEVICE_AUTO, emptyMap(),
            EffectiveSettings.NO_CALIBRATION, ThermalTier.SEVERE
        )

        assertTrue(resolved is EffectiveSettings.Resolution.Absent)
    }

    @Test
    fun `governing a preset frame rate needs no override to be present`() {
        val preset = Preset(
            id = "fast", displayName = "Fast", description = "test",
            values = mapOf(ControlId.FPS_RANGE to ControlValue.IntRangeValue(60, 60))
        )

        val resolved = EffectiveSettings.resolve(
            ControlId.FPS_RANGE, specs, preset, emptyMap(),
            EffectiveSettings.NO_CALIBRATION, ThermalTier.SEVERE
        ) as EffectiveSettings.Resolution.Resolved

        assertEquals(ControlValue.IntRangeValue(15, 30), resolved.value)
    }
}
