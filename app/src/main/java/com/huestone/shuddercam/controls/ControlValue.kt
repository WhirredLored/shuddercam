package com.huestone.shuddercam.controls

/**
 * Type-safe sealed hierarchy for heterogeneous control values.
 * Keeps the settings map type-safe without scattered casting.
 */
sealed class ControlValue {
    data class IntValue(val value: Int) : ControlValue()
    data class LongValue(val value: Long) : ControlValue()
    data class FloatValue(val value: Float) : ControlValue()
    data class BoolValue(val value: Boolean) : ControlValue()
    data class StringValue(val value: String) : ControlValue()
    data class IntRangeValue(val lower: Int, val upper: Int) : ControlValue()
}
