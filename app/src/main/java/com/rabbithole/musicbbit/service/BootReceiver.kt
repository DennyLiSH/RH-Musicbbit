package com.rabbithole.musicbbit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rabbithole.musicbbit.service.alarm.AlarmRecovery
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
 * BroadcastReceiver that re-schedules all enabled alarms after device reboot.
 *
 * AlarmManager schedules are cleared on reboot, so this receiver ensures
 * that all enabled alarms are re-registered with the system.
 *
 * Requires [Intent.ACTION_BOOT_COMPLETED] permission in AndroidManifest.
 *
 * Uses manual [EntryPointAccessors] instead of @AndroidEntryPoint field injection
 * to avoid lateinit crashes on cold boot or OEM ROMs where Hilt injection may fail.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun alarmRecovery(): AlarmRecovery
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("BootReceiver ignored action: ${intent.action}")
            return
        }

        Timber.i("BootReceiver: device boot completed, re-scheduling enabled alarms")

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Repair logic lives in AlarmRecovery — shared with startup and the
                // integrity worker. This receiver only owns the boot trigger.
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BootReceiverEntryPoint::class.java
                )
                val result = entryPoint.alarmRecovery().rescheduleEnabledAlarms()
                result.onSuccess { Timber.i("BootReceiver: rescheduled $it enabled alarms") }
                    .onFailure { throw it }
            } catch (e: Exception) {
                Timber.e(e, "BootReceiver: failed to reschedule alarms after boot")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
