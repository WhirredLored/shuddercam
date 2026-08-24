package com.huestone.shuddercam.util

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.huestone.shuddercam.controls.ThermalTier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Watches the platform thermal status and reports it as a [ThermalTier].
 *
 * The governor only observes. What each tier *does* is policy that lives with
 * the rest of the control model in controls/, so a throttled camera resolves
 * through the same single path as an unthrottled one (Law 2 and Law 4 both
 * stay intact). Transitions go to Logcat only — never to a dialog.
 */
object ThermalGovernor {

    private const val TAG = "ThermalGovernor"

    /**
     * Emits the current tier and every change to it. On API levels without a
     * thermal API this is a constant NORMAL — the app must not invent a status
     * the platform does not report.
     */
    fun tierFlow(context: Context): Flow<ThermalTier> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return flowOf(ThermalTier.NORMAL)
        }

        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return flowOf(ThermalTier.NORMAL)

        return observeFrom(power)
    }

    /**
     * The Q+ half, split out so the version guard is visible to the compiler and
     * to lint: the thermal API is used only from a function that declares it.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun observeFrom(power: PowerManager): Flow<ThermalTier> = callbackFlow {
        trySend(tierFor(power.currentThermalStatus))

        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            val tier = tierFor(status)
            Log.i(TAG, "Thermal status $status -> $tier")
            trySend(tier)
        }
        power.addThermalStatusListener(listener)

        awaitClose { power.removeThermalStatusListener(listener) }
    }.distinctUntilChanged()

    /**
     * Maps a PowerManager thermal status onto the tier that governs behaviour.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun tierFor(status: Int): ThermalTier = when {
        status <= PowerManager.THERMAL_STATUS_LIGHT -> ThermalTier.NORMAL
        status == PowerManager.THERMAL_STATUS_MODERATE -> ThermalTier.MODERATE
        else -> ThermalTier.SEVERE
    }
}
