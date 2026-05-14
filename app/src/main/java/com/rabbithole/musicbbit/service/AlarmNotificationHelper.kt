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
) : NotificationPort {

    override fun showAlarmPlaying(alarm: Alarm, song: Song) {
        createChannel()
        val notification = buildNotification(alarm, song, isPaused = false)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.notify(alarm.id.toInt(), notification)
        Timber.d("Alarm notification shown for alarmId=${alarm.id}")
    }

    override fun showAlarmPaused(alarmId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("⏰ Alarm Paused")
            .setContentText("Playback has been paused")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_notification_play,
                "Resume",
                createActionPendingIntent(alarmId, AlarmActionReceiver.ACTION_RESUME)
            )
            .addAction(
                R.drawable.ic_notification_stop,
                "Stop",
                createActionPendingIntent(alarmId, AlarmActionReceiver.ACTION_STOP)
            )
            .build()

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
        createChannel()

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("⏰ $title")
            .setContentText(message)
            .setContentIntent(contentIntent)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.notify(notificationId, notification)
        Timber.d("Error notification shown: $message")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = try {
                context.getString(R.string.notification_channel_name)
            } catch (e: android.content.res.Resources.NotFoundException) {
                "Music Alarm"
            }
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Music alarm notifications"
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Timber.d("Alarm notification channel created")
        }
    }

    private fun buildNotification(
        alarm: Alarm,
        song: Song,
        isPaused: Boolean
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            Intent(context, AlarmRingActivity::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("⏰ ${alarm.label ?: "Music Alarm"}")
            .setContentText("Playing: ${song.title} - ${song.artist ?: "Unknown artist"}")
            .setContentIntent(contentIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Playing: ${song.title} - ${song.artist ?: "Unknown artist"}")
            )

        builder.addAction(
            R.drawable.ic_notification_stop,
            "Stop",
            createActionPendingIntent(alarm.id, AlarmActionReceiver.ACTION_STOP)
        )
        builder.addAction(
            if (isPaused) R.drawable.ic_notification_play else R.drawable.ic_notification_pause,
            if (isPaused) "Resume" else "Pause",
            createActionPendingIntent(
                alarm.id,
                if (isPaused) AlarmActionReceiver.ACTION_RESUME else AlarmActionReceiver.ACTION_PAUSE
            )
        )

        builder.addAction(
            R.drawable.ic_notification_expand_more,
            "Extend ▼",
            createActionPendingIntent(alarm.id, AlarmActionReceiver.ACTION_EXTEND_MINUTES, 5)
        )

        builder.addAction(
            R.drawable.ic_notification_snooze,
            "Extend 5 min",
            createActionPendingIntent(alarm.id, AlarmActionReceiver.ACTION_EXTEND_MINUTES, 5)
        )
        builder.addAction(
            R.drawable.ic_notification_snooze,
            "Extend 10 min",
            createActionPendingIntent(alarm.id, AlarmActionReceiver.ACTION_EXTEND_MINUTES, 10)
        )
        builder.addAction(
            R.drawable.ic_notification_snooze,
            "Extend 15 min",
            createActionPendingIntent(alarm.id, AlarmActionReceiver.ACTION_EXTEND_MINUTES, 15)
        )
        builder.addAction(
            R.drawable.ic_notification_skip_next,
            "To song end",
            createActionPendingIntent(alarm.id, AlarmActionReceiver.ACTION_EXTEND_TO_END)
        )

        return builder.build()
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
