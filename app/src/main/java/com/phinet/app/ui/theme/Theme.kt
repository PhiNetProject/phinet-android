package com.phinet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Bubble colors aren't part of Material's scheme, so expose them separately. */
data class ChatColors(val bubbleOut: Color, val bubbleIn: Color)
val LocalChatColors = staticCompositionLocalOf { ChatColors(DarkBubbleOut, DarkBubbleIn) }

private val Dark = darkColorScheme(
    primary = PhiIndigo, onPrimary = DarkOn, secondary = PhiCyan, onSecondary = DarkBg,
    background = DarkBg, onBackground = DarkOn, surface = DarkSurface, onSurface = DarkOn,
    surfaceVariant = DarkSurface2, onSurfaceVariant = DarkMuted, outline = DarkOutline, error = PhiDanger,
)
private val Light = lightColorScheme(
    primary = PhiIndigo, onPrimary = Color(0xFFFFFFFF), secondary = PhiCyan, onSecondary = LightOn,
    background = LightBg, onBackground = LightOn, surface = LightSurface, onSurface = LightOn,
    surfaceVariant = LightSurface2, onSurfaceVariant = LightMuted, outline = LightOutline, error = PhiDanger,
)

@Composable
fun PhinetTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) Dark else Light
    val chat = if (dark) ChatColors(DarkBubbleOut, DarkBubbleIn)
               else ChatColors(LightBubbleOut, LightBubbleIn)
    CompositionLocalProvider(LocalChatColors provides chat) {
        MaterialTheme(colorScheme = scheme, typography = PhiTypography, content = content)
    }
}
