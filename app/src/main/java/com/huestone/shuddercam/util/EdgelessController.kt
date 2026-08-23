package com.huestone.shuddercam.util

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Edgeless mode: the preview owns every pixel of the panel, cutout and gesture
 * areas included. This touches WINDOW behaviour only — no camera setting passes
 * through here, so the one-writer rule (Law 4) is untouched.
 */
object EdgelessController {

    /**
     * Applies or lifts edgeless for [activity]. Safe to call repeatedly; every
     * call restates the whole window state, which is what makes a cold start
     * straight into edgeless behave the same as toggling into it.
     */
    fun apply(activity: Activity, enabled: Boolean, view: View) {
        val window = activity.window

        // Content extends behind the bars, so hiding or transiently showing them
        // never re-insets the layout — nothing to jump.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, view)
        if (enabled) {
            // A swipe brings the bars back as a transient overlay rather than
            // resizing anything underneath.
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        applyCutoutMode(activity, enabled)
    }

    /**
     * Renders into the display cutout. ALWAYS covers the gesture and cutout
     * regions in both orientations; SHORT_EDGES is the best available on P.
     */
    private fun applyCutoutMode(activity: Activity, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        val mode = when {
            !enabled -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Attributes are a value object: mutate then reassign to take effect.
        activity.window.attributes = activity.window.attributes.apply {
            layoutInDisplayCutoutMode = mode
        }
    }
}
