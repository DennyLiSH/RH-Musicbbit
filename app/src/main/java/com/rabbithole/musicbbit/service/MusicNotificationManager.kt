package com.rabbithole.musicbbit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rabbithole.musicbbit.MainActivity
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.service.playback.MusicNotificationPort
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the foreground notification for [MusicPlaybackService].
 *
 * Encapsulates channel creation, notification building, and posting.
 */
@Singleton
class MusicNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : MusicNotificationPort {
    private val channelId = "music_playback_channel"
    private val notificationId = 1
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun ensureChannelExists() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = try {
                context.getString(R.string.app_name)
            } catch (e: android.content.res.Resources.NotFoundException) {
                "MusicBbit"
            }
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = try {
                    context.getString(R.string.notification_music_channel_desc)
                } catch (e: android.content.res.Resources.NotFoundException) {
                    "Music playback notification"
                }
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
            Timber.i("Notification channel created")
        }
    }

    override fun buildAndNotify(state: PlaybackState) {
        val notification = buildNotification(state)
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Builds the notification for [MusicPlaybackService.startForeground].
     *
     * This method intentionally remains public (not part of [MusicNotificationPort])
     * because [android.app.Notification] is required by the Service API and must not
     * leak into the pure-Kotlin `service/playback/` seam.
     */
    fun buildNotification(state: PlaybackState): Notification {
        val song = state.currentSong

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appName = try {
            context.getString(R.string.app_name)
        } catch (e: android.content.res.Resources.NotFoundException) {
            "MusicBbit"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(song?.title ?: appName)
            .setContentText(song?.artist ?: try {
                context.getString(R.string.notification_unknown_artist)
            } catch (e: android.content.res.Resources.NotFoundException) {
                "Unknown artist"
            })
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        builder.addAction(
            R.drawable.ic_notification_skip_previous,
            try { context.getString(R.string.player_previous) } catch (_: android.content.res.Resources.NotFoundException) { "Previous" },
            createActionPendingIntent(MusicPlaybackService.ACTION_PREVIOUS)
        )

        builder.addAction(
            if (state.isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
            if (state.isPlaying) try { context.getString(R.string.pause) } catch (_: android.content.res.Resources.NotFoundException) { "Pause" } else try { context.getString(R.string.notification_play) } catch (_: android.content.res.Resources.NotFoundException) { "Play" },
            createActionPendingIntent(MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE)
        )

        builder.addAction(
            R.drawable.ic_notification_skip_next,
            try { context.getString(R.string.player_next) } catch (_: android.content.res.Resources.NotFoundException) { "Next" },
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
