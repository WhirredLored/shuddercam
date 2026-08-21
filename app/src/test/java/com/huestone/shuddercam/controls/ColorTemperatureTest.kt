package com.huestone.shuddercam.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTemperatureTest {

    @Test
    fun `warm Kelvin 3200K has higher red gain than blue gain`() {
        val gains = ColorTemperature.kelvinToRggbGains(3200)
        assertTrue("3200K red gain (${gains.red}) should be greater than blue gain (${gains.blue})", gains.red > gains.blue)
    }

    @Test
    fun `cool Kelvin 8000K has higher blue gain than red gain`() {
        val gains = ColorTemperature.kelvinToRggbGains(8000)
        assertTrue("8000K blue gain (${gains.blue}) should be greater than red gain (${gains.red})", gains.blue > gains.red)
    }

    @Test
    fun `red gain decreases monotonically as Kelvin increases`() {
        val gains3000 = ColorTemperature.kelvinToRggbGains(3000)
        val gains5000 = ColorTemperature.kelvinToRggbGains(5000)
        val gains7000 = ColorTemperature.kelvinToRggbGains(7000)

        assertTrue(gains3000.red >= gains5000.red)
        assertTrue(gains5000.red >= gains7000.red)
    }

    @Test
    fun `blue gain increases monotonically as Kelvin increases`() {
        val gains3000 = ColorTemperature.kelvinToRggbGains(3000)
        val gains5000 = ColorTemperature.kelvinToRggbGains(5000)
        val gains7000 = ColorTemperature.kelvinToRggbGains(7000)

        assertTrue(gains3000.blue <= gains5000.blue)
        assertTrue(gains5000.blue <= gains7000.blue)
    }

    @Test
    fun `green gains are always 1_0`() {
        val gains = ColorTemperature.kelvinToRggbGains(4500)
        assertEquals(1.0f, gains.greenEven, 0.001f)
        assertEquals(1.0f, gains.greenOdd, 0.001f)
    }
}
