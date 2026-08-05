package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.service.PlaybackState

/**
 * Pure-Kotlin seam for music playback notifications. No Android types leak
 * through this interface — the [ForegroundNotificationSpec] returned by
 * [buildSpec] is plain data; the `android.app.Notification` build cycle is
 * fully owned by [com.rabbithole.musicbbit.service.playback.ForegroundNotificationController]
 * (per ADR 0008).
 */
interface MusicNotificationPort {
    /** Ensure the notification channel exists. Idempotent. */
    fun ensureChannelExists()

    /** Build a pure-Kotlin spec for the foreground notification at [state]. */
    fun buildSpec(state: PlaybackState): ForegroundNotificationSpec
}
