package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for alarm CRUD operations and scheduling state management.
 */
interface AlarmRepository {
    /**
     * Returns a flow of all alarms (both enabled and disabled).
     */
    fun getAllAlarms(): Flow<List<Alarm>>

    /**
     * Returns a flow of only enabled alarms.
     */
    fun getEnabledAlarms(): Flow<List<Alarm>>

    /**
     * Returns a single alarm by its ID, or null if not found.
     */
    suspend fun getAlarmById(id: Long): Alarm?

    /**
     * Inserts a new alarm or updates an existing one.
     * Returns the ID of the saved alarm.
     */
    suspend fun saveAlarm(alarm: Alarm): Long

    /**
     * Updates an existing alarm.
     */
    suspend fun updateAlarm(alarm: Alarm)

    /**
     * Deletes an alarm.
     */
    suspend fun deleteAlarm(alarm: Alarm)

    /**
     * Toggles the enabled state of an alarm.
     */
    suspend fun enableAlarm(id: Long, enabled: Boolean)
}
