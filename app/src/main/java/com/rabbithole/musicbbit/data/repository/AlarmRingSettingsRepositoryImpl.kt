package com.rabbithole.musicbbit.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.rabbithole.musicbbit.data.local.datastore.SettingsKeys
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AlarmRingSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AlarmRingSettingsRepository {

    override fun isBreathingEnabled(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[SettingsKeys.BREATHING_ENABLED] ?: true
        }
    }

    override fun getBreathingPeriodMs(): Flow<Long> {
        return dataStore.data.map { preferences ->
            preferences[SettingsKeys.BREATHING_PERIOD_MS] ?: 3500L
        }
    }

    override suspend fun setBreathingEnabled(enabled: Boolean): Result<Unit> = withContext(ioDispatcher) {
        try {
            dataStore.edit { preferences ->
                preferences[SettingsKeys.BREATHING_ENABLED] = enabled
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setBreathingPeriodMs(periodMs: Long): Result<Unit> = withContext(ioDispatcher) {
        try {
            dataStore.edit { preferences ->
                preferences[SettingsKeys.BREATHING_PERIOD_MS] = periodMs
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
