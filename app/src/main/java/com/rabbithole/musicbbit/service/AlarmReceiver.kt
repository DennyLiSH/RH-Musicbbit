package com.rabbithole.musicbbit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rabbithole.musicbbit.service.alarm.ports.WakeLockPort
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Thin BroadcastReceiver that hands an alarm trigger off to [MusicPlaybackService].
 *
 * Holds a brief partial wake lock during the [goAsync] dispatch so the device stays awake
 * long enough to start the foreground service. Uses [WakeLockPort] via Hilt entry point
 * to stay consistent with the rest of the wake-lock management.
 *
 * All alarm bookkeeping (lastTriggeredAt write, one-time isEnabled flip, repeating-alarm
 * reschedule) lives in [com.rabbithole.musicbbit.service.alarm.AlarmFireSession.fire] and
 * runs once playback has actually started.
 */
class AlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AlarmReceiverEntryPoint {
        fun wakeLockPort(): WakeLockPort
    }

    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("AlarmReceiver triggered")

        val pendingResult = try {
            goAsync()
        } catch (e: IllegalStateException) {
            null
        }

        val wakeLockPort = EntryPointAccessors.fromApplication(
            context.applicationContext as android.app.Application,
            AlarmReceiverEntryPoint::class.java
        ).wakeLockPort()
        wakeLockPort.acquire(WAKE_LOCK_TIMEOUT_MS)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                if (alarmId == -1L) {
                    Timber.e("AlarmReceiver received invalid alarmId")
                    return@launch
                }

                val serviceIntent = MusicPlaybackService.createIntent(context).apply {
                    action = MusicPlaybackService.ACTION_PLAY_ALARM
                    putExtra(MusicPlaybackService.EXTRA_ALARM_ID, alarmId)
                    putExtra(MusicPlaybackService.EXTRA_IS_ALARM_TRIGGER, true)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
                Timber.i("Started MusicPlaybackService for alarm id=$alarmId")
            } catch (e: Exception) {
                Timber.e(e, "AlarmReceiver dispatch failed")
            } finally {
                wakeLockPort.release()
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L
    }
}
