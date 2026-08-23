package com.huestone.shuddercam.controls

import kotlin.math.max

/**
 * Data class representing RGGB color channel gains for manual white balance.
 * Pure Kotlin — no Android imports.
 */
data class RggbGains(
    val red: Float,
    val greenEven: Float,
    val greenOdd: Float,
    val blue: Float
)

/**
 * Pure Kotlin conversion from Kelvin color temperature (2000K..8000K)
 * to RGGB color correction gains.
 *
 * Algorithm based on Planckian locus / Tanner Helland approximation,
 * normalized so the green channel is the 1.0 reference.
 *
 * DIRECTION: the slider renders the selected illuminant's own tint — picking
 * 3200K yields a warm (red-heavy) frame and 8000K a cool (blue-heavy) one.
 * That is the creative "warm/cool" dial the UI presents, and it is the inverse
 * of a compensating white balance, which would cancel the illuminant instead.
 */
object ColorTemperature {

    fun kelvinToRggbGains(kelvin: Int): RggbGains {
        val temp = kelvin.coerceIn(2000, 8000) / 100.0

        // Estimate RGB values from Kelvin temperature
        val r: Double = if (temp <= 66) {
            255.0
        } else {
            329.698727446 * Math.pow(temp - 60, -0.1332047592)
        }

        val g: Double = if (temp <= 66) {
            99.4708025861 * Math.log(temp) - 161.1195681661
        } else {
            288.1221695283 * Math.pow(temp - 60, -0.0755148492)
        }

        val b: Double = if (temp >= 66) {
            255.0
        } else if (temp <= 19) {
            0.0
        } else {
            138.5177312231 * Math.log(temp - 10) - 305.0447927307
        }

        val normR = r.coerceIn(1.0, 255.0)
        val normG = g.coerceIn(1.0, 255.0)
        val normB = b.coerceIn(1.0, 255.0)

        // Gains carry the illuminant's own tint, normalized against green so the
        // reference channel stays at 1.0f: low Kelvin boosts red, high boosts blue.
        val redGain = (normR / normG).toFloat().coerceIn(0.5f, 4.0f)
        val greenGain = 1.0f
        val blueGain = (normB / normG).toFloat().coerceIn(0.5f, 4.0f)

        return RggbGains(
            red = redGain,
            greenEven = greenGain,
            greenOdd = greenGain,
            blue = blueGain
        )
    }
}
