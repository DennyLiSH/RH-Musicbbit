package com.rabbithole.musicbbit.service.alarm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic worker that verifies all enabled alarms have valid PendingIntents
 * registered with [android.app.AlarmManager].
 *
 * Delegates to [AlarmRecovery.rescheduleEnabledAlarms] (idempotent) — shared with
 * boot and startup recovery. Acts as a safety net against AlarmManager state loss
 * due to system aggressiveness (battery optimisation, force-stop, process kills).
 */
@HiltWorker
class AlarmIntegrityWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val alarmRecovery: AlarmRecovery,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return alarmRecovery.rescheduleEnabledAlarms().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val WORK_NAME = "alarm_integrity_check"
    }
}
