package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AlarmWithPlaylistName
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.AlarmScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates alarm persistence with system scheduling.
 *
 * Delegates all database work to [AlarmPersistenceRepository] and all
 * AlarmManager interactions to [AlarmScheduler]. This class
 * contains **only** orchestration logic — no direct Room or AlarmManager calls.
 */
@Singleton
internal class AlarmRepositoryImpl @Inject constructor(
    private val persistence: AlarmPersistenceRepository,
    private val alarmDao: AlarmDao,
    private val playlistDao: PlaylistDao,
    private val alarmScheduler: AlarmScheduler,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AlarmRepository {

    override fun getAllAlarms(): Flow<List<Alarm>> {
        return persistence.getAllAlarms().flowOn(ioDispatcher)
    }

    override fun getEnabledAlarms(): Flow<List<Alarm>> {
        return persistence.getEnabledAlarms().flowOn(ioDispatcher)
    }

    override fun getAlarmsWithPlaylistName(): Flow<List<AlarmWithPlaylistName>> =
        combine(
            persistence.getAllAlarms(),
            playlistDao.getAll()
        ) { alarms, playlists ->
            val namesById = playlists.associate { it.id to it.name }
            alarms.map { alarm ->
                AlarmWithPlaylistName(alarm = alarm, playlistName = namesById[alarm.playlistId])
            }
        }.flowOn(ioDispatcher)

    override suspend fun getAlarmById(id: Long): Alarm? = withContext(ioDispatcher) {
        persistence.getAlarmById(id)
    }

    override suspend fun saveAlarm(alarm: Alarm): Result<Long> = runCatching {
        withContext(ioDispatcher) {
            val id = persistence.save(alarm)
            alarmScheduler.schedule(alarm.copy(id = id))
            id
        }
    }

    override suspend fun deleteAlarm(alarm: Alarm): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            alarmScheduler.cancel(alarm.id)
            persistence.delete(alarm)
        }
    }

    override suspend fun enableAlarm(id: Long, enabled: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            val alarm = persistence.getAlarmById(id)
            if (alarm != null) {
                persistence.enableAlarm(id, enabled)
                if (enabled) {
                    alarmScheduler.schedule(alarm.copy(isEnabled = enabled))
                } else {
                    alarmScheduler.cancel(id)
                }
            }
        }
    }

    override suspend fun recordTriggered(alarmId: Long): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            persistence.recordTriggered(alarmId)
            val alarm = persistence.getAlarmById(alarmId)
            if (alarm != null && alarm.repeatDays.isNotEmpty()) {
                alarmScheduler.schedule(alarm)
            }
        }
    }
}