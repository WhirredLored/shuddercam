package com.huestone.shuddercam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huestone.shuddercam.brand.Brand
import com.huestone.shuddercam.controls.ControlId
import com.huestone.shuddercam.controls.ControlSpec
import com.huestone.shuddercam.controls.ControlValue
import com.huestone.shuddercam.controls.EffectiveSettings
import com.huestone.shuddercam.controls.Preset
import com.huestone.shuddercam.controls.PresetCodec
import com.huestone.shuddercam.controls.Presets
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The control panel: one tabbed instrument over the whole control model.
 *
 * Section order is fixed (exposure → focus → WB → motion → capture) and a tab
 * only exists when the device supports something inside it (Law 1). Every
 * control that currently carries an override shows a reset glyph, which
 * together form the at-a-glance map of what has been changed from stock.
 */
@Composable
fun ControlsSheet(
    specs: Map<ControlId, ControlSpec>,
    resolved: Map<ControlId, EffectiveSettings.Resolution>,
    overriddenIds: Set<ControlId>,
    activePreset: Preset,
    userPresets: List<Preset>,
    isAbHolding: Boolean,
    onOverride: (ControlId, ControlValue?) -> Unit,
    onSelectPreset: (Preset) -> Unit,
    onSaveCurrentAsPreset: (String) -> Unit,
    onRenamePreset: (Preset, String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onResetAll: () -> Unit,
    onAbHoldChange: (Boolean) -> Unit,
    onFocusTouching: (Boolean) -> Unit = {}
) {
    val sections = SheetSection.entries.filter { section ->
        section.controlIds.any { specs[it]?.available == true }
    }
    if (sections.isEmpty()) return

    var selected by remember(sections) { mutableStateOf(sections.first()) }
    val activeSection = if (selected in sections) selected else sections.first()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SheetHeader(
            activePreset = activePreset,
            userPresets = userPresets,
            isAbHolding = isAbHolding,
            hasOverrides = overriddenIds.isNotEmpty(),
            onSelectPreset = onSelectPreset,
            onSaveCurrentAsPreset = onSaveCurrentAsPreset,
            onRenamePreset = onRenamePreset,
            onDeletePreset = onDeletePreset,
            onResetAll = onResetAll,
            onAbHoldChange = onAbHoldChange
        )

        SectionTabs(
            sections = sections,
            selected = activeSection,
            onSelect = { selected = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (activeSection) {
            SheetSection.EXPOSURE -> ExposureSection(specs, resolved, overriddenIds, onOverride)
            SheetSection.FOCUS ->
                FocusSection(specs, resolved, overriddenIds, onOverride, onFocusTouching)
            SheetSection.WB -> WbSection(specs, resolved, overriddenIds, onOverride)
            SheetSection.MOTION -> MotionSection(specs, resolved, overriddenIds, onOverride)
            SheetSection.CAPTURE -> CaptureSection(specs, resolved, overriddenIds, onOverride)
        }
    }
}

/**
 * Fixed section order, each owning a stable list of controls.
 */
private enum class SheetSection(val label: String, val controlIds: List<ControlId>) {
    EXPOSURE(
        "Exposure",
        listOf(
            ControlId.AE_MODE,
            ControlId.ISO,
            ControlId.SHUTTER_NS,
            ControlId.AE_COMPENSATION,
            ControlId.AE_LOCK
        )
    ),
    FOCUS("Focus", listOf(ControlId.AF_MODE, ControlId.FOCUS_DISTANCE)),
    WB("WB", listOf(ControlId.AWB_MODE, ControlId.COLOR_TEMP_K, ControlId.AWB_LOCK)),
    MOTION(
        "Motion",
        listOf(
            ControlId.ZOOM_RATIO,
            ControlId.TORCH_MODE,
            ControlId.FPS_RANGE,
            ControlId.STABILIZATION_MODE
        )
    ),
    CAPTURE("Capture", listOf(ControlId.FLASH_MODE))
}

@Composable
private fun SheetHeader(
    activePreset: Preset,
    userPresets: List<Preset>,
    isAbHolding: Boolean,
    hasOverrides: Boolean,
    onSelectPreset: (Preset) -> Unit,
    onSaveCurrentAsPreset: (String) -> Unit,
    onRenamePreset: (Preset, String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onResetAll: () -> Unit,
    onAbHoldChange: (Boolean) -> Unit
) {
    var confirmVisible by remember { mutableStateOf(false) }
    var saveVisible by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Preset?>(null) }
    var menuPresetId by remember { mutableStateOf<String?>(null) }

    val takenNames = (Presets.ALL + userPresets).map { it.displayName }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Built-ins first, then the user layer — visually distinct, never mixed.
            Presets.ALL.forEach { preset ->
                PresetChip(
                    preset = preset,
                    isActive = preset.id == activePreset.id,
                    isUserPreset = false,
                    isAbHolding = isAbHolding,
                    menuOpen = false,
                    onSelect = { onSelectPreset(preset) },
                    onAbHoldChange = onAbHoldChange,
                    onOpenMenu = {},
                    onDismissMenu = {},
                    onRename = {},
                    onDelete = {}
                )
            }
            userPresets.forEach { preset ->
                PresetChip(
                    preset = preset,
                    isActive = preset.id == activePreset.id,
                    isUserPreset = true,
                    isAbHolding = isAbHolding,
                    menuOpen = menuPresetId == preset.id,
                    onSelect = { onSelectPreset(preset) },
                    onAbHoldChange = onAbHoldChange,
                    onOpenMenu = { menuPresetId = preset.id },
                    onDismissMenu = { menuPresetId = null },
                    onRename = {
                        menuPresetId = null
                        renameTarget = preset
                    },
                    onDelete = {
                        menuPresetId = null
                        onDeletePreset(preset)
                    }
                )
            }
        }

        TextButton(onClick = { saveVisible = true }) {
            Text("Save")
        }

        if (hasOverrides) {
            TextButton(onClick = { confirmVisible = true }) {
                Text("Reset all")
            }
        }
    }

    if (saveVisible) {
        PresetNameDialog(
            title = "Save current as preset",
            initialName = "",
            takenNames = takenNames,
            confirmLabel = "Save",
            onDismiss = { saveVisible = false },
            onConfirm = { name ->
                saveVisible = false
                onSaveCurrentAsPreset(name)
            }
        )
    }

    renameTarget?.let { target ->
        PresetNameDialog(
            title = "Rename preset",
            initialName = target.displayName,
            takenNames = takenNames.filterNot { it == target.displayName },
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                onRenamePreset(target, name)
            }
        )
    }

    // Switching presets never clears overrides — they shadow whatever is beneath.
    Text(
        text = activePreset.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 4.dp)
    )

    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            title = { Text("Reset all controls?") },
            text = {
                Text(
                    "Every override is cleared and the camera returns to " +
                        "${activePreset.displayName}."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmVisible = false
                    onResetAll()
                }) {
                    Text("Reset all")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVisible = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * A preset chip. The active chip auditions its preset while held (overrides
 * suspended, never cleared); a user chip additionally opens rename/delete on a
 * long press, which ends the audition first so the two never overlap.
 */
@Composable
private fun PresetChip(
    preset: Preset,
    isActive: Boolean,
    isUserPreset: Boolean,
    isAbHolding: Boolean,
    menuOpen: Boolean,
    onSelect: () -> Unit,
    onAbHoldChange: (Boolean) -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        FilterChip(
            selected = isActive && !isAbHolding,
            onClick = {},
            label = {
                Text(
                    if (isActive && isAbHolding) "${preset.displayName} (stock)"
                    else preset.displayName
                )
            },
            modifier = Modifier.pointerInput(preset.id, isActive, isUserPreset) {
                detectTapGestures(
                    onPress = {
                        if (isActive) {
                            onAbHoldChange(true)
                            tryAwaitRelease()
                            onAbHoldChange(false)
                        }
                    },
                    onTap = { if (!isActive) onSelect() },
                    onLongPress = {
                        if (isUserPreset) {
                            onAbHoldChange(false)
                            onOpenMenu()
                        }
                    }
                )
            },
            colors = if (isUserPreset) {
                // Slate marks the mutable user layer; Bone/red stays for built-ins.
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Brand.Slate.copy(alpha = 0.35f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                )
            } else {
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            }
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
            DropdownMenuItem(text = { Text("Delete") }, onClick = onDelete)
        }
    }
}

/**
 * Names a preset, refusing duplicates without scolding.
 */
@Composable
private fun PresetNameDialog(
    title: String,
    initialName: String,
    takenNames: List<String>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val isDuplicate = takenNames.any { it.equals(trimmed, ignoreCase = true) }
    val isValid = trimmed.isNotEmpty() && !isDuplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(PresetCodec.MAX_NAME_LENGTH) },
                    singleLine = true,
                    label = { Text("Name") },
                    isError = isDuplicate
                )
                if (isDuplicate) {
                    Text(
                        text = "That name is already taken — try another.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { onConfirm(trimmed) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SectionTabs(
    sections: List<SheetSection>,
    selected: SheetSection,
    onSelect: (SheetSection) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sections.forEach { section ->
            FilterChip(
                selected = section == selected,
                onClick = { onSelect(section) },
                label = { Text(section.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * Label row shared by every control: name, optional live value, and the reset
 * glyph that appears only while the control is overridden.
 */
@Composable
private fun ControlRowHeader(
    label: String,
    displayValue: String? = null,
    isOverridden: Boolean = false,
    onReset: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        if (displayValue != null) {
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        ResetGlyph(label = label, visible = isOverridden, onReset = onReset)
    }
}

/**
 * The override affordance. Occupies its 48dp slot either way so rows do not
 * shift when an override appears or is cleared.
 */
@Composable
private fun ResetGlyph(label: String, visible: Boolean, onReset: (() -> Unit)?) {
    if (visible && onReset != null) {
        IconButton(onClick = onReset, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset $label",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    } else {
        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun ExposureSection(
    specs: Map<ControlId, ControlSpec>,
    resolved: Map<ControlId, EffectiveSettings.Resolution>,
    overriddenIds: Set<ControlId>,
    onOverride: (ControlId, ControlValue?) -> Unit
) {
    val aeSpec = specs[ControlId.AE_MODE]
    if (aeSpec == null || !aeSpec.available) return

    val aeRes = resolved[ControlId.AE_MODE] as? EffectiveSettings.Resolution.Resolved ?: return
    val aeMode = (aeRes.value as? ControlValue.IntValue)?.value ?: 1
    val isManual = aeMode == 0

    ControlRowHeader(
        label = "Mode",
        isOverridden = ControlId.AE_MODE in overriddenIds,
        onReset = { onOverride(ControlId.AE_MODE, null) }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeChip("Auto", !isManual) { onOverride(ControlId.AE_MODE, ControlValue.IntValue(1)) }
        // Only show MANUAL chip if device supports AE_MODE_OFF
        if (aeSpec.allowedIntValues?.contains(0) == true) {
            ModeChip("Manual", isManual) { onOverride(ControlId.AE_MODE, ControlValue.IntValue(0)) }
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
                isOverridden = ControlId.ISO in overriddenIds,
                onReset = { onOverride(ControlId.ISO, null) },
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

            ControlSlider(
                label = "Shutter",
                value = ln(currentNs.toFloat().coerceAtLeast(1f)),
                range = ln(minNs)..ln(maxNs),
                displayValue = formatShutterSpeed(currentNs),
                isOverridden = ControlId.SHUTTER_NS in overriddenIds,
                onReset = { onOverride(ControlId.SHUTTER_NS, null) },
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
                isOverridden = ControlId.AE_COMPENSATION in overriddenIds,
                onReset = { onOverride(ControlId.AE_COMPENSATION, null) },
                onValueChange = { newVal ->
                    onOverride(ControlId.AE_COMPENSATION, ControlValue.IntValue(newVal.roundToInt()))
                }
            )
        }

        // AE Lock toggle
        val aeLockSpec = specs[ControlId.AE_LOCK]
        if (aeLockSpec != null && aeLockSpec.available) {
            val lockRes = resolved[ControlId.AE_LOCK] as? EffectiveSettings.Resolution.Resolved
            ToggleRow(
                label = "AE Lock",
                checked = (lockRes?.value as? ControlValue.BoolValue)?.value ?: false,
                isOverridden = ControlId.AE_LOCK in overriddenIds,
                onReset = { onOverride(ControlId.AE_LOCK, null) },
                onCheckedChange = { onOverride(ControlId.AE_LOCK, ControlValue.BoolValue(it)) }
            )
        }
    }
}

@Composable
private fun FocusSection(
    specs: Map<ControlId, ControlSpec>,
    resolved: Map<ControlId, EffectiveSettings.Resolution>,
    overriddenIds: Set<ControlId>,
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

    ControlRowHeader(
        label = "Focus Mode",
        isOverridden = ControlId.AF_MODE in overriddenIds,
        onReset = { onOverride(ControlId.AF_MODE, null) }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (afModes.contains(4)) {
            ModeChip("Continuous", currentAfMode == 4) {
                onOverride(ControlId.AF_MODE, ControlValue.IntValue(4))
            }
        }
        if (afModes.contains(1)) {
            ModeChip("Auto", currentAfMode == 1) {
                onOverride(ControlId.AF_MODE, ControlValue.IntValue(1))
            }
        }
        // Manual (mode 0) — only if device supports manual focus distance
        if (afModes.contains(0) && fdSpec != null && fdSpec.available) {
            ModeChip("Manual", currentAfMode == 0) {
                onOverride(ControlId.AF_MODE, ControlValue.IntValue(0))
            }
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
            isOverridden = ControlId.FOCUS_DISTANCE in overriddenIds,
            onReset = { onOverride(ControlId.FOCUS_DISTANCE, null) },
            onValueChange = { newDist ->
                onOverride(ControlId.FOCUS_DISTANCE, ControlValue.FloatValue(newDist))
            },
            onTouchChange = onFocusTouching
        )
    }
}

@Composable
private fun WbSection(
    specs: Map<ControlId, ControlSpec>,
    resolved: Map<ControlId, EffectiveSettings.Resolution>,
    overriddenIds: Set<ControlId>,
    onOverride: (ControlId, ControlValue?) -> Unit
) {
    val awbSpec = specs[ControlId.AWB_MODE]
    if (awbSpec == null || !awbSpec.available) return

    val allowedModes = awbSpec.allowedIntValues ?: return
    if (allowedModes.isEmpty()) return

    val awbRes = resolved[ControlId.AWB_MODE] as? EffectiveSettings.Resolution.Resolved ?: return
    val currentAwbMode = (awbRes.value as? ControlValue.IntValue)?.value ?: 1
    val isManualWb = currentAwbMode == 0

    val tempSpec = specs[ControlId.COLOR_TEMP_K]

    ControlRowHeader(
        label = "WB Mode",
        isOverridden = ControlId.AWB_MODE in overriddenIds,
        onReset = { onOverride(ControlId.AWB_MODE, null) }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AWB_MODE_LABELS.forEach { (mode, label) ->
            if (allowedModes.contains(mode)) {
                ModeChip(label, currentAwbMode == mode) {
                    onOverride(ControlId.AWB_MODE, ControlValue.IntValue(mode))
                }
            }
        }
        // Manual (0) — only if device supports AWB_OFF and a Kelvin range
        if (allowedModes.contains(0) && tempSpec != null && tempSpec.available) {
            ModeChip("Manual", isManualWb) {
                onOverride(ControlId.AWB_MODE, ControlValue.IntValue(0))
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (isManualWb && tempSpec != null && tempSpec.available && tempSpec.intRange != null) {
        val tempRes = resolved[ControlId.COLOR_TEMP_K] as? EffectiveSettings.Resolution.Resolved
        val currentTemp = (tempRes?.value as? ControlValue.IntValue)?.value ?: 5500

        ControlSlider(
            label = "Color Temp",
            value = currentTemp.toFloat(),
            range = tempSpec.intRange.first.toFloat()..tempSpec.intRange.last.toFloat(),
            displayValue = "${currentTemp}K",
            isOverridden = ControlId.COLOR_TEMP_K in overriddenIds,
            onReset = { onOverride(ControlId.COLOR_TEMP_K, null) },
            onValueChange = { newVal ->
                onOverride(ControlId.COLOR_TEMP_K, ControlValue.IntValue(newVal.roundToInt()))
            }
        )
    } else {
        // AWB Lock toggle (when in auto / preset AWB)
        val lockSpec = specs[ControlId.AWB_LOCK]
        if (lockSpec != null && lockSpec.available) {
            val lockRes = resolved[ControlId.AWB_LOCK] as? EffectiveSettings.Resolution.Resolved
            ToggleRow(
                label = "AWB Lock",
                checked = (lockRes?.value as? ControlValue.BoolValue)?.value ?: false,
                isOverridden = ControlId.AWB_LOCK in overriddenIds,
                onReset = { onOverride(ControlId.AWB_LOCK, null) },
                onCheckedChange = { onOverride(ControlId.AWB_LOCK, ControlValue.BoolValue(it)) }
            )
        }
    }
}

/** Named AWB modes in display order; Manual is appended separately. */
private val AWB_MODE_LABELS = listOf(
    1 to "Auto",
    5 to "Daylight",
    6 to "Cloudy",
    2 to "Tungsten"
)

@Composable
private fun MotionSection(
    specs: Map<ControlId, ControlSpec>,
    resolved: Map<ControlId, EffectiveSettings.Resolution>,
    overriddenIds: Set<ControlId>,
    onOverride: (ControlId, ControlValue?) -> Unit
) {
    val zoomSpec = specs[ControlId.ZOOM_RATIO]
    val torchSpec = specs[ControlId.TORCH_MODE]
    val fpsSpec = specs[ControlId.FPS_RANGE]
    val stabSpec = specs[ControlId.STABILIZATION_MODE]

    // Zoom slider — same override path as the viewfinder pinch gesture
    if (zoomSpec != null && zoomSpec.available && zoomSpec.floatRange != null) {
        val zoomRes = resolved[ControlId.ZOOM_RATIO] as? EffectiveSettings.Resolution.Resolved
        val currentZoom = (zoomRes?.value as? ControlValue.FloatValue)?.value ?: 1.0f

        ControlSlider(
            label = "Zoom",
            value = currentZoom,
            range = zoomSpec.floatRange,
            displayValue = String.format("%.1fx", currentZoom),
            isOverridden = ControlId.ZOOM_RATIO in overriddenIds,
            onReset = { onOverride(ControlId.ZOOM_RATIO, null) },
            onValueChange = { newZoom ->
                onOverride(ControlId.ZOOM_RATIO, ControlValue.FloatValue(newZoom))
            }
        )
    }

    // Torch toggle
    if (torchSpec != null && torchSpec.available) {
        val torchRes = resolved[ControlId.TORCH_MODE] as? EffectiveSettings.Resolution.Resolved
        ToggleRow(
            label = "Torch Light",
            checked = (torchRes?.value as? ControlValue.BoolValue)?.value ?: false,
            isOverridden = ControlId.TORCH_MODE in overriddenIds,
            onReset = { onOverride(ControlId.TORCH_MODE, null) },
            onCheckedChange = { onOverride(ControlId.TORCH_MODE, ControlValue.BoolValue(it)) }
        )
    }

    // Frame rate — only the ranges this camera reports
    val fpsOptions = fpsSpec?.allowedIntRangeValues
    if (fpsSpec != null && fpsSpec.available && !fpsOptions.isNullOrEmpty()) {
        val fpsRes = resolved[ControlId.FPS_RANGE] as? EffectiveSettings.Resolution.Resolved
        val current = fpsRes?.value as? ControlValue.IntRangeValue

        ControlRowHeader(
            label = "Frame Rate",
            isOverridden = ControlId.FPS_RANGE in overriddenIds,
            onReset = { onOverride(ControlId.FPS_RANGE, null) }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            fpsOptions.forEach { (lower, upper) ->
                ModeChip(
                    label = formatFpsRange(lower, upper),
                    selected = current?.lower == lower && current.upper == upper
                ) {
                    onOverride(ControlId.FPS_RANGE, ControlValue.IntRangeValue(lower, upper))
                }
            }
        }
    }

    // Stabilization — only the modes this camera reports
    val stabModes = stabSpec?.allowedIntValues
    if (stabSpec != null && stabSpec.available && !stabModes.isNullOrEmpty()) {
        val stabRes = resolved[ControlId.STABILIZATION_MODE] as? EffectiveSettings.Resolution.Resolved
        val currentStab = (stabRes?.value as? ControlValue.IntValue)?.value

        ControlRowHeader(
            label = "Stabilization",
            isOverridden = ControlId.STABILIZATION_MODE in overriddenIds,
            onReset = { onOverride(ControlId.STABILIZATION_MODE, null) }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            stabModes.forEach { mode ->
                ModeChip(stabilizationModeName(mode), currentStab == mode) {
                    onOverride(ControlId.STABILIZATION_MODE, ControlValue.IntValue(mode))
                }
            }
        }
    }
}

@Composable
private fun CaptureSection(
    specs: Map<ControlId, ControlSpec>,
    resolved: Map<ControlId, EffectiveSettings.Resolution>,
    overriddenIds: Set<ControlId>,
    onOverride: (ControlId, ControlValue?) -> Unit
) {
    val flashSpec = specs[ControlId.FLASH_MODE]
    if (flashSpec == null || !flashSpec.available) return

    val allowed = flashSpec.allowedIntValues ?: return
    val flashRes = resolved[ControlId.FLASH_MODE] as? EffectiveSettings.Resolution.Resolved
    val current = (flashRes?.value as? ControlValue.IntValue)?.value

    ControlRowHeader(
        label = "Flash",
        isOverridden = ControlId.FLASH_MODE in overriddenIds,
        onReset = { onOverride(ControlId.FLASH_MODE, null) }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Off / Auto / On reads better than the numeric constant order.
        FLASH_DISPLAY_ORDER.filter { it in allowed }.forEach { mode ->
            ModeChip(flashModeName(mode), current == mode) {
                onOverride(ControlId.FLASH_MODE, ControlValue.IntValue(mode))
            }
        }
    }
}

/** Off, Auto, On — CameraX numbers them 2, 0, 1 respectively. */
private val FLASH_DISPLAY_ORDER = listOf(2, 0, 1)

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    isOverridden: Boolean,
    onReset: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        )
        ResetGlyph(label = label, visible = isOverridden, onReset = onReset)
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    steps: Int = 0,
    isOverridden: Boolean = false,
    onReset: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        ControlRowHeader(
            label = label,
            displayValue = displayValue,
            isOverridden = isOverridden,
            onReset = onReset
        )
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
private fun FocusDistanceSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isOverridden: Boolean,
    onReset: () -> Unit,
    onValueChange: (Float) -> Unit,
    onTouchChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        ControlRowHeader(
            label = "Focus Distance",
            displayValue = formatDioptersAsMeters(value),
            isOverridden = isOverridden,
            onReset = onReset
        )
        Slider(
            value = value.coerceIn(range),
            onValueChange = { newVal ->
                onTouchChange(true)
                onValueChange(newVal)
            },
            onValueChangeFinished = { onTouchChange(false) },
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

/**
 * Converts diopters (focus distance) to a human-readable meters label.
 * 0 diopters = infinity; higher diopters = closer.
 */
fun formatDioptersAsMeters(diopters: Float): String {
    if (diopters <= 0.01f) return "∞"
    val meters = 1.0f / diopters
    return when {
        meters >= 5f -> String.format("%.0fm", meters)
        meters >= 1f -> String.format("%.1fm", meters)
        meters >= 0.1f -> String.format("%.0fcm", meters * 100)
        else -> "macro"
    }
}

/**
 * Formats a target FPS range as a chip label: "30" for fixed, "15–30" for a range.
 */
fun formatFpsRange(lower: Int, upper: Int): String =
    if (lower == upper) "$upper" else "$lower–$upper"

/**
 * Names a CONTROL_VIDEO_STABILIZATION_MODE value. Unknown vendor modes keep
 * their raw number rather than being hidden or renamed.
 */
fun stabilizationModeName(mode: Int): String = when (mode) {
    0 -> "Off"
    1 -> "On"
    2 -> "Preview"
    else -> "Mode $mode"
}

/**
 * Names a CameraX ImageCapture.FLASH_MODE_* value.
 */
fun flashModeName(mode: Int): String = when (mode) {
    0 -> "Auto"
    1 -> "On"
    2 -> "Off"
    else -> "Mode $mode"
}
