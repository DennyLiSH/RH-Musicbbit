package com.rabbithole.musicbbit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
        fun alarmRepository(): AlarmRepository
        fun alarmScheduler(): AlarmScheduler
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
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BootReceiverEntryPoint::class.java
                )
                val alarmRepository = entryPoint.alarmRepository()
                val alarmScheduler = entryPoint.alarmScheduler()
                val enabledAlarms = alarmRepository.getEnabledAlarms().first()
                Timber.i("BootReceiver: found ${enabledAlarms.size} enabled alarms to reschedule")
                alarmScheduler.rescheduleAll(enabledAlarms)
                Timber.i("BootReceiver: alarm rescheduling completed")
            } catch (e: Exception) {
                Timber.e(e, "BootReceiver: failed to reschedule alarms after boot")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
