package com.huestone.shuddercam.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for the user-preset layer: serialization round-trips, saved
 * values clamp on load against the current device, and deleting the active
 * preset falls back to Device Auto.
 */
class PresetCodecTest {

    private lateinit var fullSpecs: Map<ControlId, ControlSpec>
    private lateinit var narrowSpecs: Map<ControlId, ControlSpec>

    private val fullCaps = CapsMirror(
        sensitivityRange = 50..12800,
        exposureTimeRange = 1000L..2_000_000_000L,
        aeCompensationRange = -12..12,
        aeCompensationStep = 0.5f,
        availableAeModes = listOf(0, 1, 2),
        aeLockAvailable = true,
        minimumFocusDistance = 10.0f,
        availableAfModes = listOf(0, 1, 4),
        availableAwbModes = listOf(0, 1, 2, 5, 6),
        awbLockAvailable = true,
        zoomRatioRange = 1.0f..10.0f,
        flashAvailable = true,
        supportedFpsRanges = listOf(15 to 30, 30 to 30, 60 to 60),
        availableVideoStabilizationModes = listOf(0, 1),
        availableOpticalStabilizationModes = listOf(0, 1),
        hasRawCapability = true
    )

    /** Same shape, far less headroom — where a FULL-device preset must degrade. */
    private val narrowCaps = fullCaps.copy(
        sensitivityRange = 100..800,
        exposureTimeRange = 1000L..100_000_000L,
        zoomRatioRange = 1.0f..2.0f,
        supportedFpsRanges = listOf(15 to 30),
        availableAwbModes = listOf(1)
    )

    private val sample = Preset(
        id = "user_blue_hour",
        displayName = "Blue Hour",
        description = "Saved from the current settings",
        values = mapOf(
            ControlId.AE_MODE to ControlValue.IntValue(0),
            ControlId.ISO to ControlValue.IntValue(3200),
            ControlId.SHUTTER_NS to ControlValue.LongValue(500_000_000L),
            ControlId.ZOOM_RATIO to ControlValue.FloatValue(4.5f),
            ControlId.TORCH_MODE to ControlValue.BoolValue(false),
            ControlId.FPS_RANGE to ControlValue.IntRangeValue(60, 60),
            ControlId.CAPTURE_FORMAT to ControlValue.StringValue("JPEG")
        )
    )

    @Before
    fun setup() {
        fullSpecs = ControlSpec.deriveAll(fullCaps)
        narrowSpecs = ControlSpec.deriveAll(narrowCaps)
    }

    @Test
    fun `round trip preserves every value type`() {
        val decoded = PresetCodec.decode(PresetCodec.encode(sample))

        assertNotNull(decoded)
        assertEquals(sample.id, decoded!!.id)
        assertEquals(sample.displayName, decoded.displayName)
        assertEquals(sample.description, decoded.description)
        assertEquals(sample.values, decoded.values)
    }

    @Test
    fun `round trip survives an empty value set`() {
        val empty = Preset("user_empty", "Empty", "", emptyMap())

        val decoded = PresetCodec.decode(PresetCodec.encode(empty))

        assertNotNull(decoded)
        assertEquals(emptyMap<ControlId, ControlValue>(), decoded!!.values)
    }

    @Test
    fun `encoding is stable across calls`() {
        assertEquals(PresetCodec.encode(sample), PresetCodec.encode(sample))
    }

    @Test
    fun `a malformed record decodes to null rather than throwing`() {
        assertNull(PresetCodec.decode(""))
        assertNull(PresetCodec.decode("only_an_id"))
        assertNull(PresetCodec.decode("id|name")) // missing fields
        assertNull(PresetCodec.decode("|name|desc|")) // blank id
    }

    @Test
    fun `unknown controls and junk entries are skipped, the rest survives`() {
        val record = "user_x|X|desc|ISO=I:400;WARP_DRIVE=I:9;SHUTTER_NS=L:notALong;TORCH_MODE=B:true"

        val decoded = PresetCodec.decode(record)

        assertNotNull(decoded)
        assertEquals(
            mapOf(
                ControlId.ISO to ControlValue.IntValue(400),
                ControlId.TORCH_MODE to ControlValue.BoolValue(true)
            ),
            decoded!!.values
        )
    }

    @Test
    fun `names are sanitized so a record cannot be corrupted`() {
        val messy = PresetCodec.sanitizeName("  Night|Shift  ")

        assertEquals("Night Shift", messy)
        assertTrue(PresetCodec.sanitizeName("x".repeat(80)).length <= PresetCodec.MAX_NAME_LENGTH)

        // A name carrying the field delimiter still round-trips intact.
        val preset = Preset("user_ns", messy, "", mapOf(ControlId.ISO to ControlValue.IntValue(200)))
        assertEquals(messy, PresetCodec.decode(PresetCodec.encode(preset))!!.displayName)
    }

    @Test
    fun `ids derive from names and stay stable`() {
        assertEquals("user_blue_hour_2", PresetCodec.idForName("Blue Hour 2"))
        assertEquals("user_blue_hour", PresetCodec.idForName("  Blue   Hour "))
        assertEquals("user_preset", PresetCodec.idForName("!!!"))
    }

    @Test
    fun `a snapshot only captures controls the device supports`() {
        val resolved = EffectiveSettings.resolveAll(narrowSpecs, Presets.DEVICE_AUTO, emptyMap())

        val snapshot = EffectiveSettings.snapshot(resolved)

        // CAPTURE_FORMAT is absent on every device in this build.
        assertTrue(ControlId.CAPTURE_FORMAT !in snapshot)
        assertTrue(ControlId.ISO in snapshot)
    }

    @Test
    fun `a snapshot round-trips into a preset that reproduces the same values`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.AE_MODE to ControlValue.IntValue(0),
            ControlId.ISO to ControlValue.IntValue(1600)
        )
        val resolved = EffectiveSettings.resolveAll(fullSpecs, Presets.DEVICE_AUTO, overrides)

        val saved = Preset(
            id = "user_snap",
            displayName = "Snap",
            description = "Saved from the current settings",
            values = EffectiveSettings.snapshot(resolved)
        )
        val reloaded = PresetCodec.decode(PresetCodec.encode(saved))!!
        val replayed = EffectiveSettings.resolveAll(fullSpecs, reloaded, emptyMap())

        assertEquals(
            ControlValue.IntValue(1600),
            (replayed[ControlId.ISO] as EffectiveSettings.Resolution.Resolved).value
        )
        assertEquals(
            ControlValue.IntValue(0),
            (replayed[ControlId.AE_MODE] as EffectiveSettings.Resolution.Resolved).value
        )
    }

    @Test
    fun `a preset saved on a FULL device clamps onto a narrower one`() {
        val loaded = PresetCodec.decode(PresetCodec.encode(sample))!!

        val resolved = EffectiveSettings.resolveAll(narrowSpecs, loaded, emptyMap())

        val iso = resolved[ControlId.ISO] as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntValue(800), iso.value)
        assertTrue(iso.wasClamped)

        val shutter = resolved[ControlId.SHUTTER_NS] as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.LongValue(100_000_000L), shutter.value)
        assertTrue(shutter.wasClamped)

        val zoom = resolved[ControlId.ZOOM_RATIO] as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.FloatValue(2.0f), zoom.value)
        assertTrue(zoom.wasClamped)

        val fps = resolved[ControlId.FPS_RANGE] as EffectiveSettings.Resolution.Resolved
        assertEquals(ControlValue.IntRangeValue(15, 30), fps.value)
        assertTrue(fps.wasClamped)
    }

    @Test
    fun `user presets resolve with no special case in the resolver`() {
        val loaded = PresetCodec.decode(PresetCodec.encode(sample))!!
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ISO to ControlValue.IntValue(200)
        )

        val resolved = EffectiveSettings.resolve(ControlId.ISO, fullSpecs, loaded, overrides)

        // An override shadows a user preset exactly as it shadows a built-in.
        assertEquals(
            ControlValue.IntValue(200),
            (resolved as EffectiveSettings.Resolution.Resolved).value
        )
    }

    @Test
    fun `the active preset lookup finds user presets alongside built-ins`() {
        val user = listOf(sample)

        assertEquals(sample, Presets.byId("user_blue_hour", user))
        assertEquals(Presets.UV_HUNT, Presets.byId("uv_hunt", user))
    }

    @Test
    fun `deleting the active user preset falls back to device auto`() {
        val before = Presets.byId("user_blue_hour", listOf(sample))
        assertEquals(sample, before)

        // The preset is gone; the persisted id now belongs to nobody.
        val after = Presets.byId("user_blue_hour", emptyList())

        assertEquals(Presets.DEVICE_AUTO, after)
    }

    @Test
    fun `overrides are untouched when the active preset disappears`() {
        val overrides = mapOf<ControlId, ControlValue?>(
            ControlId.ISO to ControlValue.IntValue(640)
        )

        val fallback = Presets.byId("user_blue_hour", emptyList())
        val resolved = EffectiveSettings.resolve(ControlId.ISO, fullSpecs, fallback, overrides)

        assertEquals(
            ControlValue.IntValue(640),
            (resolved as EffectiveSettings.Resolution.Resolved).value
        )
    }

    @Test
    fun `built-in ids are recognised as immutable`() {
        assertTrue(Presets.isBuiltIn("uv_hunt"))
        assertTrue(Presets.isBuiltIn("device_auto"))
        assertTrue(!Presets.isBuiltIn("user_blue_hour"))
    }
}
