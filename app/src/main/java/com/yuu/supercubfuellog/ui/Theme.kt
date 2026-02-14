package com.yuu.supercubfuellog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F2937),
    secondary = Color(0xFFF3F4F6),
    tertiary = Color(0xFFFBBF24)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE5E7EB),
    secondary = Color(0xFF111827),
    tertiary = Color(0xFFFBBF24)
)

@Composable
fun SupercubFuelLogTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MaterialTheme(
            colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) DarkColors else LightColors,
            content = content
        )
    }
}
