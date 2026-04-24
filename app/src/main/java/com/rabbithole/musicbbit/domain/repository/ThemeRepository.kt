package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    fun getThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode): Result<Unit>
}
