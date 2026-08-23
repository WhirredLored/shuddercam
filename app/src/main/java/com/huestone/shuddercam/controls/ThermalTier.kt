package com.huestone.shuddercam.controls

/**
 * How hard the device is currently throttling itself.
 *
 * Pure Kotlin so the policy it implies can be resolved and unit-tested with the
 * rest of the control model; util/ThermalGovernor maps the Android
 * PowerManager status onto these tiers.
 */
enum class ThermalTier {
    /** NONE or LIGHT — full operation. */
    NORMAL,

    /** MODERATE — trim the load: frame rate capped, loupe suspended. */
    MODERATE,

    /** SEVERE and worse — most conservative frame rate, torch off, banner shown. */
    SEVERE;

    /** The highest frame rate this tier will allow, or null for no cap. */
    val fpsCeiling: Int?
        get() = when (this) {
            NORMAL -> null
            MODERATE -> MODERATE_FPS_CEILING
            SEVERE -> null // SEVERE does not cap, it picks the coolest option outright
        }

    /** The torch is a heater; it goes off before the device throttles harder. */
    val allowsTorch: Boolean
        get() = this != SEVERE

    /** The loupe re-renders while dragging, so it stands down under throttling. */
    val allowsFocusLoupe: Boolean
        get() = this == NORMAL

    companion object {
        const val MODERATE_FPS_CEILING = 30

        /**
         * Picks the frame rate range this tier will accept, given what the device
         * offers and what was asked for. Returns null when nothing needs changing.
         */
        fun governFpsRange(
            tier: ThermalTier,
            requested: ControlValue.IntRangeValue,
            available: List<Pair<Int, Int>>
        ): ControlValue.IntRangeValue? {
            if (available.isEmpty()) return null

            return when (tier) {
                NORMAL -> null

                // Cap to 30: the highest option that stays at or below the ceiling
                // and, among equals, the one with the lowest floor — a 15–30 range
                // lets auto-exposure drop the rate further, where a fixed 30 cannot.
                MODERATE -> {
                    if (requested.upper <= MODERATE_FPS_CEILING) return null
                    val capped = available.filter { it.second <= MODERATE_FPS_CEILING }
                    val choice = capped.maxWithOrNull(compareBy({ it.second }, { -it.first }))
                    // Nothing at or below the ceiling: take the coolest on offer.
                        ?: available.minByOrNull { it.second }
                        ?: return null
                    ControlValue.IntRangeValue(choice.first, choice.second)
                }

                // Coolest option the device reports, whatever was asked for.
                SEVERE -> {
                    val coolest = available.minWithOrNull(
                        compareBy({ it.second }, { it.first })
                    ) ?: return null
                    if (coolest.first == requested.lower && coolest.second == requested.upper) {
                        null
                    } else {
                        ControlValue.IntRangeValue(coolest.first, coolest.second)
                    }
                }
            }
        }
    }
}
