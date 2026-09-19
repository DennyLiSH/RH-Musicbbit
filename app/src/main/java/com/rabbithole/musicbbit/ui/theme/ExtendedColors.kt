package com.rabbithole.musicbbit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors that Material3 ColorScheme has no slot for.
 * Contrast verified per WCAG relative luminance formula:
 * - SuccessLight #2F7A3D on SurfaceContainerLowLight #F9F0EA = 4.70:1
 * - SuccessDark #9CD6A5 on SurfaceContainerLowDark #241E1B = 9.85:1
 */
data class ExtendedColors(val success: Color)

val SuccessLight = Color(0xFF2F7A3D)
val SuccessDark = Color(0xFF9CD6A5)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors(SuccessLight) }

@Composable
fun extendedColors(): ExtendedColors = LocalExtendedColors.current
