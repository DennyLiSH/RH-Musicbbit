package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetThemeModeUseCase @Inject constructor(
    private val themeRepository: ThemeRepository
) {
    operator fun invoke(): Flow<ThemeMode> {
        return themeRepository.getThemeMode()
    }
}
