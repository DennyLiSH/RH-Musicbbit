package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.datastore.SettingsKeys
import com.rabbithole.musicbbit.data.local.datastore.SettingsStore
import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ThemeRepositoryImpl @Inject constructor(
    private val settingsStore: SettingsStore,
) : ThemeRepository {

    override fun getThemeMode(): Flow<ThemeMode> =
        settingsStore.stringFlow(SettingsKeys.THEME_MODE, default = ThemeMode.SYSTEM.name)
            .map { ThemeMode.valueOf(it) }

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> =
        settingsStore.write(SettingsKeys.THEME_MODE, mode.name)
}