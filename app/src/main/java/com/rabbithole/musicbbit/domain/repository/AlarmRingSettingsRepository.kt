package com.rabbithole.musicbbit.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for alarm ring screen settings.
 *
 * Provides read/write access to user preferences that control the
 * visual behaviour of the alarm ringing screen (e.g. breathing light).
 */
interface AlarmRingSettingsRepository {

    /** Emits the current enabled state of the breathing light effect. */
    fun isBreathingEnabled(): Flow<Boolean>

    /** Emits the current breathing light period in milliseconds. */
    fun getBreathingPeriodMs(): Flow<Long>

    /** Persist whether the breathing light effect is enabled. */
    suspend fun setBreathingEnabled(enabled: Boolean): Result<Unit>

    /** Persist the breathing light period in milliseconds. */
    suspend fun setBreathingPeriodMs(periodMs: Long): Result<Unit>

    /** Emits the current volume ramp duration in seconds. 0 = ramp disabled. */
    fun getVolumeRampDurationSeconds(): Flow<Int>

    /** Persist the volume ramp duration in seconds. */
    suspend fun setVolumeRampDurationSeconds(seconds: Int): Result<Unit>
}
