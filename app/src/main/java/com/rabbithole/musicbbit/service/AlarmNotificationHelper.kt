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
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.domain.model.Song
import timber.log.Timber

/**
 * Helper object for creating and managing alarm notifications.
 *
 * Provides methods to show, update (paused state), and cancel alarm notifications.
 * Notifications include actions for stopping, pausing, and extending the alarm playback.
 */
object AlarmNotificationHelper {

    private const val CHANNEL_ID = "alarm_channel"
    private val CHANNEL_NAME_RES = R.string.notification_channel_name

    /**
     * Show the alarm notification with playback controls.
     *
     * @param context The context to use.
     * @param alarm The triggered alarm entity.
     * @param song The song currently being played.
     */
    fun show(context: Context, alarm: AlarmEntity, song: Song) {
        createChannel(context)
        val notification = buildNotification(context, alarm, song, isPaused = false)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.notify(alarm.id.toInt(), notification)
        Timber.d("Alarm notification shown for alarmId=${alarm.id}")
    }

    /**
     * Update the notification to reflect a paused state.
     *
     * @param context The context to use.
     * @param alarmId The ID of the alarm.
     */
    fun updatePaused(context: Context, alarmId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        // Build a simplified paused notification without song details
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
                createActionPendingIntent(context, alarmId, AlarmActionReceiver.ACTION_RESUME)
            )
            .addAction(
                R.drawable.ic_notification_stop,
                "Stop",
                createActionPendingIntent(context, alarmId, AlarmActionReceiver.ACTION_STOP)
            )
            .build()

        notificationManager.notify(alarmId.toInt(), notification)
        Timber.d("Alarm notification updated to paused state for alarmId=$alarmId")
    }

    /**
     * Cancel the alarm notification.
     *
     * @param context The context to use.
     * @param alarmId The ID of the alarm whose notification should be cancelled.
     */
    fun cancel(context: Context, alarmId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.cancel(alarmId.toInt())
        Timber.d("Alarm notification cancelled for alarmId=$alarmId")
    }

    /**
     * Show an error notification when the alarm cannot start playback.
     *
     * @param context The context to use.
     * @param notificationId The notification ID to use.
     * @param title The notification title.
     * @param message The error message.
     */
    fun showErrorNotification(context: Context, notificationId: Int, title: String, message: String) {
        createChannel(context)

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

    /**
     * Create the notification channel for alarm notifications (API 26+).
     *
     * @param context The context to use.
     */
    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(CHANNEL_NAME_RES),
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

    /**
     * Build the full alarm notification with all actions.
     *
     * @param context The context to use.
     * @param alarm The triggered alarm entity.
     * @param song The song currently being played.
     * @param isPaused Whether playback is paused.
     * @return The built notification.
     */
    private fun buildNotification(
        context: Context,
        alarm: AlarmEntity,
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

        // Full-screen intent for alarm ring activity
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

        // Collapsed actions (visible in compact view)
        builder.addAction(
            R.drawable.ic_notification_stop,
            "Stop",
            createActionPendingIntent(context, alarm.id, AlarmActionReceiver.ACTION_STOP)
        )
        builder.addAction(
            if (isPaused) R.drawable.ic_notification_play else R.drawable.ic_notification_pause,
            if (isPaused) "Resume" else "Pause",
            createActionPendingIntent(
                context,
                alarm.id,
                if (isPaused) AlarmActionReceiver.ACTION_RESUME else AlarmActionReceiver.ACTION_PAUSE
            )
        )

        // Extend action with dropdown behavior (shown as additional actions in expanded view)
        builder.addAction(
            R.drawable.ic_notification_expand_more,
            "Extend ▼",
            createActionPendingIntent(context, alarm.id, AlarmActionReceiver.ACTION_EXTEND_MINUTES, 5)
        )

        // Expanded actions (visible when notification is expanded)
        builder.addAction(
            R.drawable.ic_notification_snooze,
            "Extend 5 min",
            createActionPendingIntent(context, alarm.id, AlarmActionReceiver.ACTION_EXTEND_MINUTES, 5)
        )
        builder.addAction(
            R.drawable.ic_notification_snooze,
            "Extend 10 min",
            createActionPendingIntent(context, alarm.id, AlarmActionReceiver.ACTION_EXTEND_MINUTES, 10)
        )
        builder.addAction(
            R.drawable.ic_notification_snooze,
            "Extend 15 min",
            createActionPendingIntent(context, alarm.id, AlarmActionReceiver.ACTION_EXTEND_MINUTES, 15)
        )
        builder.addAction(
            R.drawable.ic_notification_skip_next,
            "To song end",
            createActionPendingIntent(context, alarm.id, AlarmActionReceiver.ACTION_EXTEND_TO_END)
        )

        return builder.build()
    }

    /**
     * Create a [PendingIntent] for a notification action.
     *
     * @param context The context to use.
     * @param alarmId The ID of the alarm.
     * @param action The action string.
     * @param minutes Optional minutes value for extend actions.
     * @return A PendingIntent targeting [AlarmActionReceiver].
     */
    private fun createActionPendingIntent(
        context: Context,
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
}
