package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

/**
 * Pure persistence operations for alarms — no system scheduling.
 *
 * See [AlarmRepository] for the full repository that coordinates persistence
 * with [com.rabbithole.musicbbit.service.AlarmScheduler].
 */
interface AlarmPersistenceRepository {
    fun getAllAlarms(): Flow<List<Alarm>>
    fun getEnabledAlarms(): Flow<List<Alarm>>
    suspend fun getAlarmById(id: Long): Alarm?
    suspend fun save(alarm: Alarm): Long
    suspend fun update(alarm: Alarm)
    suspend fun delete(alarm: Alarm)
    suspend fun enableAlarm(id: Long, enabled: Boolean)
    suspend fun recordTriggered(alarmId: Long)
}
