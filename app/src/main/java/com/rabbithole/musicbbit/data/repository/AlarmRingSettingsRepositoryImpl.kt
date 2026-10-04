package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.datastore.SettingsKeys
import com.rabbithole.musicbbit.data.local.datastore.SettingsStore
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AlarmRingSettingsRepositoryImpl @Inject constructor(
    private val settingsStore: SettingsStore,
) : AlarmRingSettingsRepository {

    override fun isBreathingEnabled(): Flow<Boolean> =
        settingsStore.booleanFlow(SettingsKeys.BREATHING_ENABLED, default = true)

    override fun getBreathingPeriodMs(): Flow<Long> =
        settingsStore.longFlow(SettingsKeys.BREATHING_PERIOD_MS, default = 3500L)

    override suspend fun setBreathingEnabled(enabled: Boolean): Result<Unit> =
        settingsStore.write(SettingsKeys.BREATHING_ENABLED, enabled)

    override suspend fun setBreathingPeriodMs(periodMs: Long): Result<Unit> =
        settingsStore.write(SettingsKeys.BREATHING_PERIOD_MS, periodMs)

    override fun getVolumeRampDurationSeconds(): Flow<Int> =
        settingsStore.intFlow(SettingsKeys.VOLUME_RAMP_DURATION_SECONDS, default = 5)

    override suspend fun setVolumeRampDurationSeconds(seconds: Int): Result<Unit> =
        settingsStore.write(SettingsKeys.VOLUME_RAMP_DURATION_SECONDS, seconds)
}