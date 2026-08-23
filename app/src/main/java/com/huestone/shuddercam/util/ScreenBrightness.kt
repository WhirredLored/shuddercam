package com.huestone.shuddercam.util

import android.app.Activity
import android.view.WindowManager

/**
 * Brightness assist for TrueSight: the illusion dies if the panel is dimmer
 * than the world beside it.
 *
 * This only ever writes this app's own window attribute. System brightness
 * settings are never touched, and leaving TrueSight hands control straight back
 * to the system value.
 */
object ScreenBrightness {

    fun boost(activity: Activity) {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
    }

    fun restore(activity: Activity) {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
}
