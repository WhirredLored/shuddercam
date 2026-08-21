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
 * Algorithm based on Planckian locus / Tanner Helland approximation
 * normalized relative to D65 (~6500K neutral baseline).
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

        // Gains are inverse to emitted RGB color to balance back to white.
        // Normalized so green gain = 1.0f (reference channel).
        val redGain = (normG / normR).toFloat().coerceIn(0.5f, 4.0f)
        val greenGain = 1.0f
        val blueGain = (normG / normB).toFloat().coerceIn(0.5f, 4.0f)

        return RggbGains(
            red = redGain,
            greenEven = greenGain,
            greenOdd = greenGain,
            blue = blueGain
        )
    }
}
