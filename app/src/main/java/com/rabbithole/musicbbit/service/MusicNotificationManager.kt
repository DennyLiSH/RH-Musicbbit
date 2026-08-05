package com.rabbithole.musicbbit.service

import android.content.Context
import android.app.NotificationManager
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.service.playback.ForegroundNotificationSpec
import com.rabbithole.musicbbit.service.playback.MusicNotificationPort
import com.rabbithole.musicbbit.service.PlaybackState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts [MusicNotificationPort] to Android resources. Builds pure-Kotlin
 * [ForegroundNotificationSpec] values; the controller is responsible for
 * rendering them into an `android.app.Notification` and calling
 * `startForeground` on the Service (per ADR 0008).
 */
@Singleton
internal class MusicNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resources: NotificationResources,
    private val channelFactory: NotificationChannelFactory,
) : MusicNotificationPort {
    private val channelId = "music_playback_channel"
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun ensureChannelExists() {
        channelFactory.ensureChannel(
            channelId = channelId,
            nameRes = R.string.app_name,
            nameFallback = "MusicBbit",
            descRes = R.string.notification_music_channel_desc,
            descFallback = "Music playback notification",
            importance = NotificationManager.IMPORTANCE_LOW
        )
    }

    override fun buildSpec(state: PlaybackState): ForegroundNotificationSpec {
        val song = state.currentSong
        val appName = resources.getString(R.string.app_name, "MusicBbit")
        val unknownArtist = resources.getString(R.string.notification_unknown_artist, "Unknown artist")

        val playPauseLabel = if (state.isPlaying)
            resources.getString(R.string.pause, "Pause")
        else
            resources.getString(R.string.notification_play, "Play")

        return ForegroundNotificationSpec(
            title = song?.title ?: appName,
            text = song?.artist ?: unknownArtist,
            isPlaying = state.isPlaying,
            smallIconResId = R.drawable.ic_notification_small,
            playPauseIconResId = if (state.isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
            playPauseLabel = playPauseLabel,
            previousLabel = resources.getString(R.string.player_previous, "Previous"),
            nextLabel = resources.getString(R.string.player_next, "Next"),
        )
    }
}
