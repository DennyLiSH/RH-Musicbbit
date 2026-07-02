package com.rabbithole.musicbbit.domain.model

/**
 * Defines how the alarm should surface when it fires.
 */
enum class AlarmRingMode {
    /** Shows a high-priority notification without waking the screen. */
    Normal,

    /** Wakes the screen and launches the full-screen ring interface. */
    FullScreen,
}
