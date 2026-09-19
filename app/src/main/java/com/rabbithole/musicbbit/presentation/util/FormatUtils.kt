package com.rabbithole.musicbbit.presentation.util

import java.util.Locale

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatClockTime(hour: Int, minute: Int): String = "%02d:%02d".format(Locale.US, hour, minute)

