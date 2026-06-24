package com.rabbithole.musicbbit.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Easing

/**
 * Motion design tokens — single source of truth for animation durations and
 * easings across the app. Eliminates ad-hoc `tween(durationMillis = N, easing = ...)`
 * magic numbers duplicated in AlarmListScreen, MusicBrowseScreen, PlaylistListScreen,
 * MiniPlayer, etc.
 *
 * Convention:
 * - [DurationShort] (150ms): micro interactions (ripple, hover)
 * - [DurationMedium] (250ms): standard transitions (swipe-dismiss, drag-end)
 * - [DurationLong] (300ms): screen-level transitions (Crossfade, AnimatedVisibility)
 * - [EasingEmphasized]: FastOutSlowInEasing (Material standard ease-out)
 *
 * Usage:
 * ```
 * tween(durationMillis = MotionTokens.DurationLong, easing = MotionTokens.EasingEmphasized)
 * ```
 */
object MotionTokens {
    const val DurationShort = 150
    const val DurationMedium = 250
    const val DurationLong = 300

    val EasingEmphasized: Easing = FastOutSlowInEasing
}
