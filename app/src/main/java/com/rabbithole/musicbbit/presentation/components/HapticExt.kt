package com.rabbithole.musicbbit.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import timber.log.Timber

/**
 * Click modifier with haptic feedback. Wraps [HapticFeedback.performHapticFeedback]
 * in runCatching to defend against device ROMs that throw when the vibrator service
 * is unavailable (some emulators / low-end OEM ROMs). 80ms debounce prevents rapid
 * taps from spamming the vibrator (e.g. during list scroll).
 *
 * Callers that cannot use a clickable Modifier (e.g. Switch onCheckedChange) should
 * use [performHapticSafe] directly on the LocalHapticFeedback instance.
 */
fun Modifier.hapticClick(
    type: HapticFeedbackType = HapticFeedbackType.LongPress,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    var lastClickMs by remember { mutableLongStateOf(0L) }
    clickable {
        val now = System.currentTimeMillis()
        if (now - lastClickMs >= DEBOUNCE_MS) {
            lastClickMs = now
            haptic.performHapticSafe(type)
        }
        onClick()
    }
}

/**
 * Haptic-feedback helper for non-clickable call sites (e.g. Switch onCheckedChange).
 * Same try/catch defense and Timber warning as [hapticClick].
 */
fun HapticFeedback.performHapticSafe(
    type: HapticFeedbackType = HapticFeedbackType.LongPress
) {
    runCatching { performHapticFeedback(type) }
        .onFailure { Timber.w("haptic_unavailable: ${it.javaClass.simpleName}") }
}

private const val DEBOUNCE_MS = 80L
