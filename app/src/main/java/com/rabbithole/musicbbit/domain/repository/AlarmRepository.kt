package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

/**
 * Alarm aggregate: persistence + AlarmManager scheduling, coordinated.
 *
 * Error contract: writes return [Result] (failure = persistence or scheduling error,
 * never throws); single reads return nullable; flows never throw — upstream Room
 * errors surface as flow cancellation.
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
    suspend fun saveAlarm(alarm: Alarm): Result<Long>

    /**
     * Deletes an alarm.
     */
    suspend fun deleteAlarm(alarm: Alarm): Result<Unit>

    /**
     * Toggles the enabled state of an alarm.
     */
    suspend fun enableAlarm(id: Long, enabled: Boolean): Result<Unit>

    /**
     * Record that an alarm has been triggered. Updates [Alarm.lastTriggeredAt],
     * disables one-time alarms, and reschedules repeating alarms.
     */
    suspend fun recordTriggered(alarmId: Long): Result<Unit>
}
