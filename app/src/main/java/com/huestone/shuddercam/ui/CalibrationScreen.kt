package com.huestone.shuddercam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huestone.shuddercam.controls.EffectiveSettings

/** Screen-pixels of drag that span the whole calibration range. */
private const val DRAG_SPAN_PX = 900f

/**
 * TrueSight calibration. The user drags until what is on screen continues the
 * real scene across the phone's edge; the factor they land on is empirical
 * because lens FOV, arm length and eye distance are jointly unknowable.
 *
 * The live preview stays visible underneath — this overlay only adds the
 * instruction, the edge rulers, and the confirm bar.
 */
@Composable
fun CalibrationScreen(
    initialFactor: Float,
    isPortrait: Boolean,
    onFactorChange: (Float) -> Unit,
    onConfirm: (Float) -> Unit,
    onCancel: () -> Unit
) {
    var factor by remember { mutableFloatStateOf(initialFactor) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isPortrait) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Dragging up magnifies, which matches "pull the world closer".
                    val span = EffectiveSettings.CALIBRATION_RANGE.endInclusive -
                        EffectiveSettings.CALIBRATION_RANGE.start
                    val next = (factor - dragAmount / DRAG_SPAN_PX * span)
                        .coerceIn(EffectiveSettings.CALIBRATION_RANGE)
                    factor = next
                    onFactorChange(next)
                }
            }
    ) {
        AlignmentRuler(modifier = Modifier.align(Alignment.CenterStart))
        AlignmentRuler(modifier = Modifier.align(Alignment.CenterEnd))

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 32.dp, end = 32.dp)
        ) {
            Text(
                text = "TrueSight calibration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Hold the phone at arm's length. Look at an object past the " +
                    "phone's edge. Drag until the object on screen matches its real " +
                    "size and lines up with what you see beside the phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                    .padding(12.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%.2f×  ·  %s", factor, if (isPortrait) "portrait" else "landscape"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(factor) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}

/**
 * A thin ruler down a side edge. Matching its ticks to the world either side of
 * the bezel is what makes continuity judgeable rather than guessed.
 */
@Composable
private fun AlignmentRuler(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(10.dp)
            .fillMaxHeight()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(21) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
            )
        }
    }
}
