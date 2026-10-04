package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

/**
 * Internal persistence seam for [com.rabbithole.musicbbit.domain.repository.AlarmRepositoryImpl].
 * Not part of the domain vocabulary — only the scheduling-aware [com.rabbithole.musicbbit.domain.repository.AlarmRepository]
 * is exposed to consumers.
 *
 * Pure persistence operations for alarms — no system scheduling.
 */
interface AlarmPersistenceRepository {
    fun getAllAlarms(): Flow<List<Alarm>>
    fun getEnabledAlarms(): Flow<List<Alarm>>
    suspend fun getAlarmById(id: Long): Alarm?
    suspend fun save(alarm: Alarm): Long
    suspend fun delete(alarm: Alarm)
    suspend fun enableAlarm(id: Long, enabled: Boolean)
    suspend fun recordTriggered(alarmId: Long)
}