package com.huestone.shuddercam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huestone.shuddercam.controls.ControlId
import com.huestone.shuddercam.controls.ControlSpec
import com.huestone.shuddercam.controls.ControlValue
import com.huestone.shuddercam.controls.EffectiveSettings
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Bottom control sheet on the viewfinder. Currently implements the EXPOSURE
 * section; extended by later commits for focus, WB, zoom, etc.
 */
@Composable
fun ControlsSheet(
    specs: Map<ControlId, ControlSpec>,
    resolved: Map<ControlId, EffectiveSettings.Resolution>,
    onOverride: (ControlId, ControlValue?) -> Unit,
    onFocusTouching: (Boolean) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ExposureSection(specs, resolved, onOverride)
        FocusSection(specs, resolved, onOverride, onFocusTouching)
    }
}

@Composable
private fun ExposureSection(
    specs: Map<ControlId, ControlSpec>,
    resolved: Map<ControlId, EffectiveSettings.Resolution>,
    onOverride: (ControlId, ControlValue?) -> Unit
) {
    val aeSpec = specs[ControlId.AE_MODE]
    if (aeSpec == null || !aeSpec.available) return

    val aeRes = resolved[ControlId.AE_MODE] as? EffectiveSettings.Resolution.Resolved ?: return
    val aeMode = (aeRes.value as? ControlValue.IntValue)?.value ?: 1
    val isManual = aeMode == 0

    SectionLabel("Exposure")

    // AUTO / MANUAL chip
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = !isManual,
            onClick = { onOverride(ControlId.AE_MODE, ControlValue.IntValue(1)) },
            label = { Text("Auto") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                selectedLabelColor = MaterialTheme.colorScheme.primary
            )
        )
        // Only show MANUAL chip if device supports AE_MODE_OFF
        val hasManual = aeSpec.allowedIntValues?.contains(0) == true
        if (hasManual) {
            FilterChip(
                selected = isManual,
                onClick = { onOverride(ControlId.AE_MODE, ControlValue.IntValue(0)) },
                label = { Text("Manual") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (isManual) {
        // ISO slider
        val isoSpec = specs[ControlId.ISO]
        if (isoSpec != null && isoSpec.available && isoSpec.intRange != null) {
            val isoRes = resolved[ControlId.ISO] as? EffectiveSettings.Resolution.Resolved
            val currentIso = (isoRes?.value as? ControlValue.IntValue)?.value ?: isoSpec.intRange.first

            ControlSlider(
                label = "ISO",
                value = currentIso.toFloat(),
                range = isoSpec.intRange.first.toFloat()..isoSpec.intRange.last.toFloat(),
                displayValue = "$currentIso",
                onValueChange = { newVal ->
                    onOverride(ControlId.ISO, ControlValue.IntValue(newVal.roundToInt()))
                }
            )
        }

        // Shutter speed slider (log scale)
        val shutterSpec = specs[ControlId.SHUTTER_NS]
        if (shutterSpec != null && shutterSpec.available && shutterSpec.longRange != null) {
            val shutterRes = resolved[ControlId.SHUTTER_NS] as? EffectiveSettings.Resolution.Resolved
            val currentNs = (shutterRes?.value as? ControlValue.LongValue)?.value
                ?: shutterSpec.longRange.first

            val minNs = shutterSpec.longRange.first.toFloat().coerceAtLeast(1f)
            val maxNs = shutterSpec.longRange.last.toFloat()
            val logMin = ln(minNs)
            val logMax = ln(maxNs)
            val logCurrent = ln(currentNs.toFloat().coerceAtLeast(1f))

            ControlSlider(
                label = "Shutter",
                value = logCurrent,
                range = logMin..logMax,
                displayValue = formatShutterSpeed(currentNs),
                onValueChange = { logVal ->
                    val ns = kotlin.math.exp(logVal).roundToLong()
                        .coerceIn(shutterSpec.longRange)
                    onOverride(ControlId.SHUTTER_NS, ControlValue.LongValue(ns))
                }
            )
        }
    } else {
        // AE Compensation (EV) slider
        val evSpec = specs[ControlId.AE_COMPENSATION]
        if (evSpec != null && evSpec.available && evSpec.intRange != null) {
            val evRes = resolved[ControlId.AE_COMPENSATION] as? EffectiveSettings.Resolution.Resolved
            val currentEv = (evRes?.value as? ControlValue.IntValue)?.value ?: 0

            ControlSlider(
                label = "EV",
                value = currentEv.toFloat(),
                range = evSpec.intRange.first.toFloat()..evSpec.intRange.last.toFloat(),
                displayValue = if (currentEv >= 0) "+$currentEv" else "$currentEv",
                steps = evSpec.intRange.last - evSpec.intRange.first - 1,
                onValueChange = { newVal ->
                    onOverride(ControlId.AE_COMPENSATION, ControlValue.IntValue(newVal.roundToInt()))
                }
            )
        }

        // AE Lock toggle
        val aeLockSpec = specs[ControlId.AE_LOCK]
        if (aeLockSpec != null && aeLockSpec.available) {
            val lockRes = resolved[ControlId.AE_LOCK] as? EffectiveSettings.Resolution.Resolved
            val locked = (lockRes?.value as? ControlValue.BoolValue)?.value ?: false

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AE Lock",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = locked,
                    onCheckedChange = { newLocked ->
                        onOverride(ControlId.AE_LOCK, ControlValue.BoolValue(newLocked))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/**
 * Formats nanoseconds as human-readable shutter speed.
 */
fun formatShutterSpeed(ns: Long): String {
    val seconds = ns / 1_000_000_000.0
    return when {
        seconds >= 1.0 -> String.format("%.1fs", seconds)
        seconds >= 0.1 -> String.format("%.2fs", seconds)
        else -> {
            val frac = (1.0 / seconds).roundToInt()
            "1/${frac}"
        }
    }
}

@Composable
private fun FocusSection(
    specs: Map<ControlId, ControlSpec>,
    resolved: Map<ControlId, EffectiveSettings.Resolution>,
    onOverride: (ControlId, ControlValue?) -> Unit,
    onFocusTouching: (Boolean) -> Unit
) {
    val afSpec = specs[ControlId.AF_MODE]
    if (afSpec == null || !afSpec.available) return

    // Fixed-focus devices get no focus section (Law 1)
    val fdSpec = specs[ControlId.FOCUS_DISTANCE]
    val afModes = afSpec.allowedIntValues ?: return
    if (afModes.isEmpty()) return

    val afRes = resolved[ControlId.AF_MODE] as? EffectiveSettings.Resolution.Resolved ?: return
    val currentAfMode = (afRes.value as? ControlValue.IntValue)?.value ?: 4

    Spacer(modifier = Modifier.height(12.dp))
    SectionLabel("Focus")

    // AF mode chips: Continuous, Auto, Manual
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Continuous Picture (mode 4)
        if (afModes.contains(4)) {
            FilterChip(
                selected = currentAfMode == 4,
                onClick = { onOverride(ControlId.AF_MODE, ControlValue.IntValue(4)) },
                label = { Text("Continuous") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        // Auto (mode 1)
        if (afModes.contains(1)) {
            FilterChip(
                selected = currentAfMode == 1,
                onClick = { onOverride(ControlId.AF_MODE, ControlValue.IntValue(1)) },
                label = { Text("Auto") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        // Manual (mode 0) — only if device supports manual focus distance
        if (afModes.contains(0) && fdSpec != null && fdSpec.available) {
            FilterChip(
                selected = currentAfMode == 0,
                onClick = { onOverride(ControlId.AF_MODE, ControlValue.IntValue(0)) },
                label = { Text("Manual") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Manual focus distance slider — only when AF is off
    if (currentAfMode == 0 && fdSpec != null && fdSpec.available && fdSpec.floatRange != null) {
        val fdRes = resolved[ControlId.FOCUS_DISTANCE] as? EffectiveSettings.Resolution.Resolved
        val currentDist = (fdRes?.value as? ControlValue.FloatValue)?.value ?: 0f

        FocusDistanceSlider(
            value = currentDist,
            range = fdSpec.floatRange,
            onValueChange = { newDist ->
                onOverride(ControlId.FOCUS_DISTANCE, ControlValue.FloatValue(newDist))
            },
            onTouchChange = onFocusTouching
        )
    }
}

@Composable
private fun FocusDistanceSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onTouchChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Focus Distance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Text(
                text = formatDioptersAsMeters(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = { newVal ->
                onTouchChange(true)
                onValueChange(newVal)
            },
            onValueChangeFinished = {
                onTouchChange(false)
            },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        )
    }
}

/**
 * Converts diopters (focus distance) to a human-readable meters label.
 * 0 diopters = infinity; higher diopters = closer.
 */
private fun formatDioptersAsMeters(diopters: Float): String {
    if (diopters <= 0.01f) return "∞"
    val meters = 1.0f / diopters
    return when {
        meters >= 5f -> String.format("%.0fm", meters)
        meters >= 1f -> String.format("%.1fm", meters)
        meters >= 0.1f -> String.format("%.0fcm", meters * 100)
        else -> "macro"
    }
}
