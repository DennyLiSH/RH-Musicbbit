package com.rabbithole.musicbbit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * BroadcastReceiver that handles alarm trigger events from [AlarmManager].
 *
 * Responsibilities:
 * - Acquire a partial wake lock to ensure the device stays awake during processing
 * - Load the triggered alarm and its associated playlist
 * - Determine the starting song index based on saved playback progress
 * - Reset playback progress for the starting song
 * - Start [MusicPlaybackService] as a foreground service
 * - Update the alarm's [lastTriggeredAt] timestamp
 * - Reschedule repeating alarms for their next occurrence
 * - Display an alarm notification via [AlarmNotificationHelper]
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmDao: AlarmDao

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

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

                Timber.i("Processing alarm id=$alarmId")

                // Immediately start foreground service with just the alarm ID.
                // The service will acquire its own wake lock and load the playlist.
                val serviceIntent = MusicPlaybackService.createIntent(context).apply {
                    action = MusicPlaybackService.ACTION_PLAY_ALARM
                    putExtra(MusicPlaybackService.EXTRA_ALARM_ID, alarmId)
                    putExtra(MusicPlaybackService.EXTRA_IS_ALARM_TRIGGER, true)
                }
                context.startForegroundService(serviceIntent)
                Timber.i("Started MusicPlaybackService for alarm id=$alarmId")

                // Background tasks: validate alarm, update timestamp, reschedule
                val alarm = alarmDao.getById(alarmId)
                if (alarm == null) {
                    Timber.w("Alarm id=$alarmId not found in database")
                    return@launch
                }

                if (!alarm.isEnabled) {
                    Timber.d("Alarm id=$alarmId is disabled, ignoring trigger")
                    return@launch
                }

                // One-time alarms (repeatDaysBitmask == 0) are auto-disabled after trigger.
                // Repeating alarms keep isEnabled=true and are rescheduled for the next occurrence.
                val isOneTime = alarm.repeatDaysBitmask == 0
                val updatedAlarm = alarm.copy(
                    lastTriggeredAt = System.currentTimeMillis(),
                    isEnabled = if (isOneTime) false else alarm.isEnabled
                )
                alarmDao.update(updatedAlarm)
                Timber.i(
                    "Updated alarm id=$alarmId: lastTriggeredAt=${updatedAlarm.lastTriggeredAt}, " +
                        "isEnabled=${updatedAlarm.isEnabled}, isOneTime=$isOneTime"
                )

                if (!isOneTime) {
                    Timber.i("Rescheduling repeating alarm id=$alarmId")
                    alarmScheduler.schedule(updatedAlarm)
                }
            } catch (e: Exception) {
                Timber.e(e, "AlarmReceiver processing failed")
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
