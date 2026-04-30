package com.rabbithole.musicbbit.service.playback

import android.app.Notification
import com.rabbithole.musicbbit.service.PlaybackState

interface MusicNotificationPort {
    fun createChannel()
    fun buildNotification(state: PlaybackState): Notification
    fun notify(notification: Notification)
}
