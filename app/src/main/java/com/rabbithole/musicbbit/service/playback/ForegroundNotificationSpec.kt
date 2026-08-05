package com.rabbithole.musicbbit.service.playback

import androidx.annotation.DrawableRes
import com.rabbithole.musicbbit.service.PlaybackState

/**
 * Pure-Kotlin description of what the foreground music notification should look
 * like at a given [PlaybackState]. The controller converts this into an
 * `android.app.Notification` via `NotificationCompat.Builder`.
 *
 * Keeping the spec free of Android `Notification` / `PendingIntent` types means
 * the spec can be asserted on in JVM pure-unit tests without Robolectric, per
 * ADR 0008.
 */
data class ForegroundNotificationSpec(
    val title: String,
    val text: String,
    val isPlaying: Boolean,
    @DrawableRes val smallIconResId: Int,
    @DrawableRes val playPauseIconResId: Int,
    val playPauseLabel: String,
    val previousLabel: String,
    val nextLabel: String,
)
