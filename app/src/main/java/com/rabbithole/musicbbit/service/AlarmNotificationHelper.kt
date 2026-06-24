package com.rabbithole.musicbbit.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.presentation.alarm.AlarmRingActivity
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.alarm.ports.NotificationPort
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages alarm notifications — show, update (paused state), cancel, and error display.
 *
 * Implements [NotificationPort] directly, replacing the former static-object + adapter pair.
 * Notifications include actions for stopping, pausing, and extending the alarm playback.
 */
@Singleton
class AlarmNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resources: NotificationResources,
    private val channelFactory: NotificationChannelFactory,
    private val mainActivityIntentFactory: MainActivityIntentFactory,
) : NotificationPort {

    private val contentBuilder = AlarmNotificationContentBuilder(
        defaultAlarmLabel = resources.getString(R.string.notification_default_alarm_label, "Music Alarm"),
        unknownArtist = resources.getString(R.string.notification_unknown_artist, "Unknown artist"),
        playingFormat = resources.getString(R.string.notification_playing_format, "Playing: %1\$s - %2\$s"),
        stop = resources.getString(R.string.stop, "Stop"),
        pause = resources.getString(R.string.pause, "Pause"),
        resume = resources.getString(R.string.resume, "Resume"),
        extend = resources.getString(R.string.notification_extend, "Extend ▼"),
        extendMinutesFormat = resources.getString(R.string.notification_extend_minutes, "Extend %d min"),
        toSongEnd = resources.getString(R.string.notification_to_song_end, "To song end"),
        alarmPausedTitle = resources.getString(R.string.notification_alarm_paused, "Alarm Paused"),
        playbackPausedText = resources.getString(R.string.notification_playback_paused, "Playback has been paused"),
    )

    override fun showAlarmPlaying(alarm: Alarm, song: Song) {
        channelFactory.ensureChannel(
            channelId = CHANNEL_ID,
            nameRes = R.string.notification_channel_name,
            nameFallback = "Music Alarm",
            descRes = R.string.notification_alarm_channel_desc,
            descFallback = "Music alarm notifications",
            importance = NotificationManager.IMPORTANCE_HIGH
        )
        val content = contentBuilder.buildPlaying(alarm.label, song.title, song.artist)
        val notification = renderNotification(content, alarm.id)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.notify(alarm.id.toInt(), notification)
        Timber.d("Alarm notification shown for alarmId=${alarm.id}")
    }

    override fun showAlarmPaused(alarmId: Long) {
        val content = contentBuilder.buildPaused()
        val notification = renderNotification(content, alarmId)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.notify(alarmId.toInt(), notification)
        Timber.d("Alarm notification updated to paused state for alarmId=$alarmId")
    }

    override fun cancel(alarmId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.cancel(alarmId.toInt())
        Timber.d("Alarm notification cancelled for alarmId=$alarmId")
    }

    override fun showError(notificationId: Int, title: String, message: String) {
        channelFactory.ensureChannel(
            channelId = CHANNEL_ID,
            nameRes = R.string.notification_channel_name,
            nameFallback = "Music Alarm",
            descRes = R.string.notification_alarm_channel_desc,
            descFallback = "Music alarm notifications",
            importance = NotificationManager.IMPORTANCE_HIGH
        )
        val content = contentBuilder.buildError(title, message)
        val notification = renderNotification(content, alarmId = notificationId.toLong())
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.notify(notificationId, notification)
        Timber.d("Error notification shown: $message")
    }

    private fun renderNotification(content: AlarmNotificationContent, alarmId: Long): Notification {
        val contentIntent = mainActivityIntentFactory.create(0)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(contentIntent)
            .setOngoing(content.isOngoing)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(content.autoCancel)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        content.bigText?.let {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }

        if (content.showFullScreenIntent) {
            val fullScreenIntent = PendingIntent.getActivity(
                context,
                alarmId.toInt(),
                Intent(context, AlarmRingActivity::class.java).apply {
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenIntent, true)
        }

        content.actions.forEach { action ->
            builder.addAction(
                action.iconResId,
                action.label,
                createActionPendingIntentForType(alarmId, action.type)
            )
        }

        return builder.build()
    }

    private fun createActionPendingIntentForType(
        alarmId: Long,
        type: AlarmNotificationContent.ActionType
    ): PendingIntent {
        val (action, minutes) = when (type) {
            is AlarmNotificationContent.ActionType.Stop ->
                AlarmActionReceiver.ACTION_STOP to null
            is AlarmNotificationContent.ActionType.Pause ->
                AlarmActionReceiver.ACTION_PAUSE to null
            is AlarmNotificationContent.ActionType.Resume ->
                AlarmActionReceiver.ACTION_RESUME to null
            is AlarmNotificationContent.ActionType.ExtendMinutes ->
                AlarmActionReceiver.ACTION_EXTEND_MINUTES to type.minutes
            is AlarmNotificationContent.ActionType.ExtendToEnd ->
                AlarmActionReceiver.ACTION_EXTEND_TO_END to null
        }
        return createActionPendingIntent(alarmId, action, minutes)
    }

    private fun createActionPendingIntent(
        alarmId: Long,
        action: String,
        minutes: Int? = null
    ): PendingIntent {
        val intent = Intent(context, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            minutes?.let { putExtra(AlarmActionReceiver.EXTRA_MINUTES, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt() + action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val CHANNEL_ID = "alarm_channel"
    }
}
