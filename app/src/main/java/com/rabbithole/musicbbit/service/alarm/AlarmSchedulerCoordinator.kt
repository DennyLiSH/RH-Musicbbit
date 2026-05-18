package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.service.AlarmScheduler
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates alarm persistence changes with the system [AlarmScheduler].
 *
 * Keeps scheduling concerns separate from repository persistence so that
 * [com.rabbithole.musicbbit.data.repository.AlarmRepositoryImpl] does not
 * mix database operations with AlarmManager interactions.
 */
@Singleton
class AlarmSchedulerCoordinator @Inject constructor(
    private val alarmScheduler: AlarmScheduler,
) {

    /** Schedule an alarm after it has been persisted. */
    suspend fun schedule(alarm: Alarm) {
        alarmScheduler.schedule(alarm)
        Timber.i("Alarm scheduled id=${alarm.id}")
    }

    /** Cancel an alarm before it is deleted. */
    fun cancel(alarmId: Long) {
        alarmScheduler.cancel(alarmId)
        Timber.i("Alarm cancelled id=$alarmId")
    }
}
