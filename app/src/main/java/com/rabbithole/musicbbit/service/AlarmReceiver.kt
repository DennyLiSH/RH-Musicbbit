package com.rabbithole.musicbbit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Thin BroadcastReceiver that hands an alarm trigger off to [MusicPlaybackService].
 *
 * Holds a brief partial wake lock during the [goAsync] dispatch so the device stays awake
 * long enough to start the foreground service.
 *
 * All alarm bookkeeping (lastTriggeredAt write, one-time isEnabled flip, repeating-alarm
 * reschedule) lives in [com.rabbithole.musicbbit.service.alarm.AlarmFireSession.fire] and
 * runs once playback has actually started.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("AlarmReceiver triggered")

        val pendingResult = goAsync()
        val wakeLock = acquireWakeLock(context)
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
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Timber.d("WakeLock released")
                    }
                }
                pendingResult.finish()
            }
        }
    }

    /**
     * Acquire a partial wake lock with a 60-second timeout.
     *
     * @param context The context to use.
     * @return The acquired wake lock, or null if acquisition failed.
     */
    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
                Timber.d("WakeLock acquired")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire wake lock")
            null
        }
    }

    companion object {
        private const val WAKE_LOCK_TAG = "RH-Musicbbit::AlarmWakeLock"
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L
    }
}
