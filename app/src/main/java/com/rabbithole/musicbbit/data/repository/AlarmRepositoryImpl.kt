package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmPersistenceRepository
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.alarm.AlarmSchedulerCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates alarm persistence with system scheduling.
 *
 * Delegates all database work to [AlarmPersistenceRepository] and all
 * AlarmManager interactions to [AlarmSchedulerCoordinator]. This class
 * contains **only** orchestration logic — no direct Room or AlarmManager calls.
 */
@Singleton
class AlarmRepositoryImpl @Inject constructor(
    private val persistence: AlarmPersistenceRepository,
    private val schedulerCoordinator: AlarmSchedulerCoordinator,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AlarmRepository {

    override fun getAllAlarms(): Flow<List<Alarm>> {
        return persistence.getAllAlarms().flowOn(ioDispatcher)
    }

    override fun getEnabledAlarms(): Flow<List<Alarm>> {
        return persistence.getEnabledAlarms().flowOn(ioDispatcher)
    }

    override suspend fun getAlarmById(id: Long): Alarm? = withContext(ioDispatcher) {
        persistence.getAlarmById(id)
    }

    override suspend fun saveAlarm(alarm: Alarm): Result<Long> = runCatching {
        withContext(ioDispatcher) {
            val id = persistence.save(alarm)
            schedulerCoordinator.schedule(alarm.copy(id = id))
            id
        }
    }

    override suspend fun updateAlarm(alarm: Alarm): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            persistence.update(alarm)
            schedulerCoordinator.schedule(alarm)
        }
    }

    override suspend fun deleteAlarm(alarm: Alarm): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            schedulerCoordinator.cancel(alarm.id)
            persistence.delete(alarm)
        }
    }

    override suspend fun enableAlarm(id: Long, enabled: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            val alarm = persistence.getAlarmById(id)
            if (alarm != null) {
                persistence.enableAlarm(id, enabled)
                if (enabled) {
                    schedulerCoordinator.schedule(alarm.copy(isEnabled = enabled))
                } else {
                    schedulerCoordinator.cancel(id)
                }
            }
        }
    }

    override suspend fun recordTriggered(alarmId: Long): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            persistence.recordTriggered(alarmId)
            val alarm = persistence.getAlarmById(alarmId)
            if (alarm != null && alarm.repeatDays.isNotEmpty()) {
                schedulerCoordinator.schedule(alarm)
            }
        }
    }
}
