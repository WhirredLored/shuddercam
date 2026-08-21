package com.huestone.shuddercam.brand

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for all brand strings and color tokens.
 * No other file may define brand colors or app identity strings.
 */
object Brand {
    // ── Color tokens ──
    val Void = Color(0xFF0B0B0E)
    val Bone = Color(0xFFEDE6DC)
    val ShudderRed = Color(0xFFD93A2B)
    val Slate = Color(0xFF5A5F66)

    // ── Brand strings ──
    const val APP_NAME = "ShudderCam"
    const val COMPANY = "Huestone Holdings"
}
