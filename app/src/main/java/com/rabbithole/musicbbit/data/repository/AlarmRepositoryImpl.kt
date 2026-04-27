package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.toBitmask
import com.rabbithole.musicbbit.domain.model.toDayOfWeekSet
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
            alarmScheduler.schedule(entity.copy(id = id))
            id
        }
    }

    override suspend fun updateAlarm(alarm: Alarm): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            val entity = alarm.toEntity()
            alarmDao.update(entity)
            Timber.i("Alarm updated id=${alarm.id}, re-scheduling")
            alarmScheduler.schedule(entity)
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
                alarmScheduler.schedule(updated)
            } else {
                Timber.w("Attempted to enable/disable non-existent alarm id=$id")
            }
        }
    }

    private fun AlarmEntity.toDomain(): Alarm {
        return Alarm(
            id = id,
            hour = hour,
            minute = minute,
            repeatDays = repeatDaysBitmask.toDayOfWeekSet(),
            excludeHolidays = excludeHolidays,
            playlistId = playlistId,
            isEnabled = isEnabled,
            label = label,
            autoStopMinutes = autoStopMinutes,
            lastTriggeredAt = lastTriggeredAt
        )
    }

    private fun Alarm.toEntity(): AlarmEntity {
        return AlarmEntity(
            id = id,
            hour = hour,
            minute = minute,
            repeatDaysBitmask = repeatDays.toBitmask(),
            excludeHolidays = excludeHolidays,
            playlistId = playlistId,
            isEnabled = isEnabled,
            label = label,
            autoStopMinutes = autoStopMinutes,
            lastTriggeredAt = lastTriggeredAt
        )
    }
}
