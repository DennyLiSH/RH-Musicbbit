package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.domain.repository.ThemeRepository
import javax.inject.Inject

class SetThemeModeUseCase @Inject constructor(
    private val themeRepository: ThemeRepository
) {
    suspend operator fun invoke(mode: ThemeMode): Result<Unit> {
        return themeRepository.setThemeMode(mode)
    }
}
