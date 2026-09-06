package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.AlarmScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep module owning alarm schedule recovery: "scan enabled alarms → repair schedule
 * and state". One implementation serves all three recovery triggers:
 *
 *   - Device boot ([com.rabbithole.musicbbit.service.BootReceiver]) → [rescheduleEnabledAlarms]
 *   - App startup reconciliation ([AlarmStartupReconciler]) → [recoverAll]
 *   - Periodic integrity worker ([AlarmIntegrityWorker]) → [rescheduleEnabledAlarms]
 *
 * [rescheduleEnabledAlarms] is the plain idempotent path (rescheduleAll cancels then
 * re-registers). [recoverAll] additionally repairs state drifted by a failed
 * [AlarmFireSession] bookkeeping: a one-shot alarm that already fired but is still
 * enabled gets disabled, instead of being re-registered.
 *
 * Per-alarm failures are logged and skipped so one bad alarm cannot block the rest.
 * A failure to load the alarm list surfaces as [Result.failure] so callers that care
 * (the integrity worker retries; boot and startup just log) can decide.
 */
@Singleton
class AlarmRecovery @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun rescheduleEnabledAlarms(): Result<Int> = runPass { enabledAlarms ->
        if (enabledAlarms.isEmpty()) {
            Timber.i("AlarmRecovery: no enabled alarms, nothing to reschedule")
            return@runPass
        }
        Timber.i("AlarmRecovery: rescheduling ${enabledAlarms.size} enabled alarms")
        alarmScheduler.rescheduleAll(enabledAlarms)
        Timber.i("AlarmRecovery: rescheduling completed")
    }

    suspend fun recoverAll(): Result<Int> = runPass { enabledAlarms ->
        Timber.i("AlarmRecovery: scanning ${enabledAlarms.size} enabled alarms")

        for (alarm in enabledAlarms) {
            try {
                when {
                    // One-shot alarm that has already triggered but was not disabled
                    // (bookkeep failed after playback started)
                    alarm.repeatDays.isEmpty() && alarm.lastTriggeredAt != null -> {
                        Timber.w(
                            "AlarmRecovery: disabling one-shot alarm ${alarm.id} " +
                                "(lastTriggeredAt=${alarm.lastTriggeredAt})"
                        )
                        alarmRepository.enableAlarm(alarm.id, false)
                    }

                    // Repeating alarm: ensure system-side schedule is up to date
                    alarm.repeatDays.isNotEmpty() -> {
                        Timber.i("AlarmRecovery: rescheduling repeating alarm ${alarm.id}")
                        alarmScheduler.rescheduleAll(listOf(alarm))
                    }

                    // One-shot not yet triggered: reschedule (PendingIntent may be lost)
                    else -> {
                        Timber.i("AlarmRecovery: rescheduling one-shot alarm ${alarm.id} (pendingIntent may be lost)")
                        alarmScheduler.schedule(alarm)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "AlarmRecovery: failed to process alarm ${alarm.id}")
            }
        }
    }

    private suspend fun runPass(block: suspend (List<Alarm>) -> Unit): Result<Int> {
        return try {
            val enabledAlarms = alarmRepository.getEnabledAlarms().first()
            block(enabledAlarms)
            Result.success(enabledAlarms.size)
        } catch (e: Exception) {
            Timber.e(e, "AlarmRecovery: recovery pass failed")
            Result.failure(e)
        }
    }
}
