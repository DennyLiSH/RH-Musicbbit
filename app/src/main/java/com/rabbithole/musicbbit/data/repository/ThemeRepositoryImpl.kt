package com.rabbithole.musicbbit.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.domain.repository.ThemeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ThemeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ThemeRepository {

    override fun getThemeMode(): Flow<ThemeMode> {
        return dataStore.data.map { preferences ->
            val modeString = preferences[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(modeString)
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = withContext(ioDispatcher) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_THEME_MODE] = mode.name
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
