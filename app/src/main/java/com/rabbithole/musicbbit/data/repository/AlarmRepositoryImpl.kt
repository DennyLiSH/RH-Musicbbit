package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.mapper.AlarmMapper.toDomain
import com.rabbithole.musicbbit.data.mapper.AlarmMapper.toEntity
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.AlarmScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementation of [AlarmRepository] that persists alarms via Room and
 * keeps the system [AlarmScheduler] in sync.
 */
class AlarmRepositoryImpl @Inject constructor(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AlarmRepository {

    override fun getAllAlarms(): Flow<List<Alarm>> {
        return alarmDao.getAll()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun getEnabledAlarms(): Flow<List<Alarm>> {
        return alarmDao.getEnabledAlarms()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getAlarmById(id: Long): Alarm? = withContext(ioDispatcher) {
        alarmDao.getById(id)?.toDomain()
    }

    override suspend fun saveAlarm(alarm: Alarm): Result<Long> = runCatching {
        withContext(ioDispatcher) {
            val entity = alarm.toEntity()
            val id = alarmDao.insert(entity)
            Timber.i("Alarm saved with id=$id, scheduling")
            alarmScheduler.schedule(alarm.copy(id = id))
            id
        }
    }

    override suspend fun updateAlarm(alarm: Alarm): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            val entity = alarm.toEntity()
            alarmDao.update(entity)
            Timber.i("Alarm updated id=${alarm.id}, re-scheduling")
            alarmScheduler.schedule(alarm)
        }
    }

    override suspend fun deleteAlarm(alarm: Alarm): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            Timber.i("Deleting alarm id=${alarm.id}, cancelling first")
            alarmScheduler.cancel(alarm.id)
            alarmDao.delete(alarm.toEntity())
        }
    }

    override suspend fun enableAlarm(id: Long, enabled: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            val existing = alarmDao.getById(id)
            if (existing != null) {
                val updated = existing.copy(isEnabled = enabled)
                alarmDao.update(updated)
                Timber.i("Alarm id=$id enabled=$enabled, updating scheduler")
                alarmScheduler.schedule(updated.toDomain())
            } else {
                Timber.w("Attempted to enable/disable non-existent alarm id=$id")
            }
        }
    }

    override suspend fun recordTriggered(alarmId: Long) = withContext(ioDispatcher) {
        try {
            val entity = alarmDao.getById(alarmId) ?: return@withContext
            val isOneTime = entity.repeatDaysBitmask == 0
            val updated = entity.copy(
                lastTriggeredAt = System.currentTimeMillis(),
                isEnabled = if (isOneTime) false else entity.isEnabled,
            )
            alarmDao.update(updated)
            Timber.i("Recorded trigger for alarm id=$alarmId, isOneTime=$isOneTime")

            if (!isOneTime) {
                alarmScheduler.schedule(updated.toDomain())
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to record trigger for alarm id=$alarmId")
        }
    }

}
