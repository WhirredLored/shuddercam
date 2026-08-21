package com.huestone.shuddercam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ShudderDarkColorScheme = darkColorScheme(
    primary = ShudderRed,
    onPrimary = Bone,
    secondary = Slate,
    onSecondary = Bone,
    background = Void,
    onBackground = Bone,
    surface = VoidSurface,
    onSurface = Bone,
    surfaceVariant = VoidLight,
    onSurfaceVariant = Bone,
    error = ShudderRed,
    onError = Void
)

@Composable
fun ShudderCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShudderDarkColorScheme,
        content = content
    )
}
