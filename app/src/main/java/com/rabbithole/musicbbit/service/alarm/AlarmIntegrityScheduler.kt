package com.rabbithole.musicbbit.service.alarm

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the periodic [AlarmIntegrityWorker] that verifies alarm PendingIntents
 * are still registered with the system.
 *
 * Scheduled on every app start via [AlarmStartupReconciler]. Uses
 * [ExistingPeriodicWorkPolicy.KEEP] so the 15-minute interval is not reset
 * if a previous request is still active.
 */
@Singleton
class AlarmIntegrityScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<AlarmIntegrityWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AlarmIntegrityWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Timber.i("AlarmIntegrityScheduler: scheduled periodic integrity check (15min)")
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(AlarmIntegrityWorker.WORK_NAME)
        Timber.i("AlarmIntegrityScheduler: cancelled periodic integrity check")
    }
}
