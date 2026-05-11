package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.service.PlaybackState

/**
 * Pure-Kotlin seam for playback notifications. No Android types leak through
 * this interface — the [android.app.Notification] build/post cycle is fully
 * encapsulated by the adapter.
 */
interface MusicNotificationPort {
    fun ensureChannelExists()
    fun buildAndNotify(state: PlaybackState)
}
