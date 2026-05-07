package com.siepic.gimbal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = GimbalOrange,
    onPrimary = GimbalBlack,
    secondary = GimbalOrangeDim,
    background = GimbalBlack,
    onBackground = GimbalLightGray,
    surface = GimbalDarkGray,
    onSurface = GimbalLightGray,
    error = GimbalRed,
)

@Composable
fun GimbalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = GimbalTypography,
        content = content,
    )
}
