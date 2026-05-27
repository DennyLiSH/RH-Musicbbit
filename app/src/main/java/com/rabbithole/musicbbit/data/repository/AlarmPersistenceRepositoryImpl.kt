package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.mapper.toDomain
import com.rabbithole.musicbbit.data.mapper.toEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmPersistenceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [AlarmPersistenceRepository].
 *
 * Contains **only** database operations — no AlarmManager scheduling.
 */
@Singleton
class AlarmPersistenceRepositoryImpl @Inject constructor(
    private val alarmDao: AlarmDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AlarmPersistenceRepository {

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

    override suspend fun save(alarm: Alarm): Long = withContext(ioDispatcher) {
        val entity = alarm.toEntity()
        val id = alarmDao.insert(entity)
        Timber.i("Alarm persisted with id=$id")
        id
    }

    override suspend fun update(alarm: Alarm) = withContext(ioDispatcher) {
        val entity = alarm.toEntity()
        alarmDao.update(entity)
        Timber.i("Alarm updated id=${alarm.id}")
    }

    override suspend fun delete(alarm: Alarm) = withContext(ioDispatcher) {
        alarmDao.delete(alarm.toEntity())
        Timber.i("Alarm deleted id=${alarm.id}")
    }

    override suspend fun enableAlarm(id: Long, enabled: Boolean) = withContext(ioDispatcher) {
        val existing = alarmDao.getById(id)
        if (existing != null) {
            val updated = existing.copy(isEnabled = enabled)
            alarmDao.update(updated)
            Timber.i("Alarm id=$id enabled=$enabled")
        } else {
            Timber.w("Attempted to enable/disable non-existent alarm id=$id")
        }
    }

    override suspend fun recordTriggered(alarmId: Long) = withContext(ioDispatcher) {
        val entity = alarmDao.getById(alarmId) ?: return@withContext
        val isOneTime = entity.repeatDaysBitmask == 0
        val updated = entity.copy(
            lastTriggeredAt = System.currentTimeMillis(),
            isEnabled = if (isOneTime) false else entity.isEnabled,
        )
        alarmDao.update(updated)
        Timber.i("Recorded trigger for alarm id=$alarmId, isOneTime=$isOneTime")
    }
}
