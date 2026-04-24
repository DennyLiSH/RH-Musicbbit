package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import javax.inject.Inject

class SavePlaybackProgressUseCase @Inject constructor(
    private val playbackProgressRepository: PlaybackProgressRepository
) {
    suspend operator fun invoke(
        songId: Long,
        positionMs: Long,
        playlistId: Long
    ): Result<Unit> = runCatching {
        playbackProgressRepository.saveProgress(
            PlaybackProgress(
                songId = songId,
                positionMs = positionMs,
                updatedAt = System.currentTimeMillis(),
                playlistId = playlistId
            )
        )
    }
}
