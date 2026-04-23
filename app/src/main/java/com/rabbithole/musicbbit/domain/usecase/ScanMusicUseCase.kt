package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.repository.MusicRepository
import javax.inject.Inject

class ScanMusicUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return musicRepository.refreshSongs()
    }
}
