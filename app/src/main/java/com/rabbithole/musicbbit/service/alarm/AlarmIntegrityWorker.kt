package com.rabbithole.musicbbit.service.alarm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.AlarmScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Periodic worker that verifies all enabled alarms have valid PendingIntents
 * registered with [android.app.AlarmManager].
 *
 * If enabled alarms are found, calls [AlarmScheduler.rescheduleAll] which
 * is idempotent (cancels then reschedules). This acts as a safety net against
 * AlarmManager state loss due to system aggressiveness (e.g. battery optimisation,
 * force-stop, or process kills between reboots).
 */
@HiltWorker
class AlarmIntegrityWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val enabledAlarms = alarmRepository.getEnabledAlarms().first()
            if (enabledAlarms.isEmpty()) {
                Timber.i("AlarmIntegrityWorker: no enabled alarms, skipping")
                return Result.success()
            }
            Timber.i("AlarmIntegrityWorker: checking ${enabledAlarms.size} enabled alarms")
            alarmScheduler.rescheduleAll(enabledAlarms)
            Timber.i("AlarmIntegrityWorker: rescheduling completed")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "AlarmIntegrityWorker: failed")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "alarm_integrity_check"
    }
}
