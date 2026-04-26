package com.rabbithole.musicbbit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rabbithole.musicbbit.service.alarm.AlarmFireSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

/**
 * BroadcastReceiver that handles user actions from the alarm notification.
 *
 * Each action calls [AlarmFireSession] directly. Notification updates and state
 * transitions live inside the session, so there is no service-intent round trip.
 *
 * Supported actions:
 * - [ACTION_STOP]: Stop the alarm session (host stops playback, session releases
 *   wake lock + cancels notification)
 * - [ACTION_PAUSE]: Pause playback and show paused notification
 * - [ACTION_RESUME]: Resume paused playback
 * - [ACTION_EXTEND_MINUTES]: Extend the auto-stop timer by [EXTRA_MINUTES] minutes
 * - [ACTION_EXTEND_TO_END]: Stop playback when the current song finishes
 */
@AndroidEntryPoint
class AlarmActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmFireSession: AlarmFireSession

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)

        if (alarmId == -1L) {
            Timber.e("AlarmActionReceiver received invalid alarmId")
            return
        }

        Timber.i("AlarmActionReceiver action=$action, alarmId=$alarmId")

        when (action) {
            ACTION_STOP -> alarmFireSession.stop()
            ACTION_PAUSE -> alarmFireSession.pause()
            ACTION_RESUME -> alarmFireSession.resume()
            ACTION_EXTEND_MINUTES -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                if (minutes <= 0) {
                    Timber.w("Invalid extend minutes: $minutes")
                    return
                }
                alarmFireSession.extendAutoStop(minutes)
            }
            ACTION_EXTEND_TO_END -> alarmFireSession.setExtendToEnd(true)
            else -> Timber.w("Unknown action received: $action")
        }
    }

    companion object {
        const val ACTION_STOP = "com.rabbithole.musicbbit.action.STOP_ALARM"
        const val ACTION_PAUSE = "com.rabbithole.musicbbit.action.PAUSE_ALARM"
        const val ACTION_RESUME = "com.rabbithole.musicbbit.action.RESUME_ALARM"
        const val ACTION_EXTEND_MINUTES = "com.rabbithole.musicbbit.action.EXTEND_MINUTES"
        const val ACTION_EXTEND_TO_END = "com.rabbithole.musicbbit.action.EXTEND_TO_END"

        const val EXTRA_MINUTES = "extra_minutes"
    }
}
