package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rabbithole.musicbbit.domain.model.Song
import timber.log.Timber

/**
 * BroadcastReceiver that handles user actions from the alarm notification.
 *
 * Supported actions:
 * - [ACTION_STOP]: Stop playback and dismiss the notification
 * - [ACTION_PAUSE]: Pause playback and update notification to paused state
 * - [ACTION_EXTEND_MINUTES]: Extend auto-stop timer by specified minutes
 * - [ACTION_EXTEND_TO_END]: Set auto-stop to trigger at the end of current song
 *
 * Each action carries [AlarmScheduler.EXTRA_ALARM_ID] to identify the target alarm.
 */
class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)

        if (alarmId == -1L) {
            Timber.e("AlarmActionReceiver received invalid alarmId")
            return
        }

        Timber.i("AlarmActionReceiver action=$action, alarmId=$alarmId")

        when (action) {
            ACTION_STOP -> handleStop(context, alarmId)
            ACTION_PAUSE -> handlePause(context, alarmId)
            ACTION_RESUME -> handleResume(context, alarmId)
            ACTION_EXTEND_MINUTES -> handleExtendMinutes(context, intent, alarmId)
            ACTION_EXTEND_TO_END -> handleExtendToEnd(context, alarmId)
            else -> Timber.w("Unknown action received: $action")
        }
    }

    /**
     * Stop playback and cancel the alarm notification.
     *
     * @param context The context to use.
     * @param alarmId The ID of the alarm being stopped.
     */
    private fun handleStop(context: Context, alarmId: Long) {
        Timber.i("Stopping alarm playback for alarmId=$alarmId")

        val serviceIntent = MusicPlaybackService.createIntent(context).apply {
            action = ACTION_SERVICE_STOP
        }
        context.startService(serviceIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.cancel(alarmId.toInt())
        Timber.d("Cancelled notification for alarmId=$alarmId")
    }

    /**
     * Pause playback and update the notification to show paused state.
     *
     * @param context The context to use.
     * @param alarmId The ID of the alarm being paused.
     */
    private fun handlePause(context: Context, alarmId: Long) {
        Timber.i("Pausing alarm playback for alarmId=$alarmId")

        val serviceIntent = MusicPlaybackService.createIntent(context).apply {
            action = ACTION_SERVICE_PAUSE
        }
        context.startService(serviceIntent)

        // Update notification to paused state
        // The song info is retrieved from the service via a broadcast or
        // we use a placeholder since the service state will be updated
        AlarmNotificationHelper.updatePaused(context, alarmId)
        Timber.d("Updated notification to paused state for alarmId=$alarmId")
    }

    /**
     * Resume playback. The notification update is handled by the service's player listener.
     *
     * @param context The context to use.
     * @param alarmId The ID of the alarm being resumed.
     */
    private fun handleResume(context: Context, alarmId: Long) {
        Timber.i("Resuming alarm playback for alarmId=$alarmId")

        val serviceIntent = MusicPlaybackService.createIntent(context).apply {
            action = ACTION_SERVICE_RESUME
        }
        context.startService(serviceIntent)

        // Notification will be updated by MusicPlaybackService's player listener
        Timber.d("Sent resume command to service for alarmId=$alarmId")
    }

    /**
     * Extend the auto-stop timer by the specified number of minutes.
     *
     * @param context The context to use.
     * @param intent The intent carrying [EXTRA_MINUTES].
     * @param alarmId The ID of the alarm being extended.
     */
    private fun handleExtendMinutes(context: Context, intent: Intent, alarmId: Long) {
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        if (minutes <= 0) {
            Timber.w("Invalid extend minutes: $minutes")
            return
        }

        Timber.i("Extending alarm id=$alarmId by $minutes minutes")

        val serviceIntent = MusicPlaybackService.createIntent(context).apply {
            action = ACTION_SERVICE_EXTEND_MINUTES
            putExtra(EXTRA_MINUTES, minutes)
        }
        context.startService(serviceIntent)
    }

    /**
     * Set the auto-stop to trigger at the end of the current song.
     *
     * @param context The context to use.
     * @param alarmId The ID of the alarm being extended.
     */
    private fun handleExtendToEnd(context: Context, alarmId: Long) {
        Timber.i("Extending alarm id=$alarmId to end of current song")

        val serviceIntent = MusicPlaybackService.createIntent(context).apply {
            action = ACTION_SERVICE_EXTEND_TO_END
        }
        context.startService(serviceIntent)
    }

    companion object {
        const val ACTION_STOP = "com.rabbithole.musicbbit.action.STOP_ALARM"
        const val ACTION_PAUSE = "com.rabbithole.musicbbit.action.PAUSE_ALARM"
        const val ACTION_RESUME = "com.rabbithole.musicbbit.action.RESUME_ALARM"
        const val ACTION_EXTEND_MINUTES = "com.rabbithole.musicbbit.action.EXTEND_MINUTES"
        const val ACTION_EXTEND_TO_END = "com.rabbithole.musicbbit.action.EXTEND_TO_END"

        const val EXTRA_MINUTES = "extra_minutes"

        // Internal actions sent to MusicPlaybackService
        const val ACTION_SERVICE_STOP = "com.rabbithole.musicbbit.action.SERVICE_STOP"
        const val ACTION_SERVICE_PAUSE = "com.rabbithole.musicbbit.action.SERVICE_PAUSE"
        const val ACTION_SERVICE_RESUME = "com.rabbithole.musicbbit.action.SERVICE_RESUME"
        const val ACTION_SERVICE_EXTEND_MINUTES = "com.rabbithole.musicbbit.action.SERVICE_EXTEND_MINUTES"
        const val ACTION_SERVICE_EXTEND_TO_END = "com.rabbithole.musicbbit.action.SERVICE_EXTEND_TO_END"
    }
}
