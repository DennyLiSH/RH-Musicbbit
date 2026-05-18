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

    private val contentBuilder = AlarmNotificationContentBuilder()

    override fun showAlarmPlaying(alarm: Alarm, song: Song) {
        createChannel()
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
        createChannel()
        val content = contentBuilder.buildError(title, message)
        val notification = renderNotification(content, alarmId = notificationId.toLong())
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

    private fun renderNotification(content: AlarmNotificationContent, alarmId: Long): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
