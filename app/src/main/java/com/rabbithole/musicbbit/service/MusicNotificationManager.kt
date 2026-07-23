package com.rabbithole.musicbbit.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.service.playback.MusicNotificationPort
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the foreground notification for [MusicPlaybackService].
 *
 * Encapsulates channel creation, notification building, and the Android
 * foreground service lifecycle cycle. The [android.app.Notification] type
 * never leaves this adapter.
 */
@Singleton
internal class MusicNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resources: NotificationResources,
    private val channelFactory: NotificationChannelFactory,
    private val mainActivityIntentFactory: MainActivityIntentFactory,
    private val foregroundServicePort: ForegroundServicePort,
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

    override fun buildAndNotify(state: PlaybackState) {
        val notification = buildNotification(state)
        Timber.i("Starting foreground notification for playback")
        foregroundServicePort.startForeground(notification)
    }

    override fun hideForegroundNotification() {
        Timber.i("Stopping foreground notification for playback")
        foregroundServicePort.stopForeground()
    }

    /**
     * Builds the notification for the foreground service.
     *
     * This method is private to keep [android.app.Notification] inside this
     * adapter and out of the pure-Kotlin `service/playback/` seam.
     */
    private fun buildNotification(state: PlaybackState): Notification {
        val song = state.currentSong

        val contentIntent = mainActivityIntentFactory.create(0)

        val appName = resources.getString(R.string.app_name, "MusicBbit")
        val unknownArtist = resources.getString(R.string.notification_unknown_artist, "Unknown artist")

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(song?.title ?: appName)
            .setContentText(song?.artist ?: unknownArtist)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        builder.addAction(
            R.drawable.ic_notification_skip_previous,
            resources.getString(R.string.player_previous, "Previous"),
            createActionPendingIntent(MusicPlaybackService.ACTION_PREVIOUS)
        )

        val playPauseLabel = if (state.isPlaying)
            resources.getString(R.string.pause, "Pause")
        else
            resources.getString(R.string.notification_play, "Play")
        builder.addAction(
            if (state.isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
            playPauseLabel,
            createActionPendingIntent(MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE)
        )

        builder.addAction(
            R.drawable.ic_notification_skip_next,
            resources.getString(R.string.player_next, "Next"),
            createActionPendingIntent(MusicPlaybackService.ACTION_NEXT)
        )

        return builder.build()
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, MusicPlaybackService::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
