package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import javax.inject.Inject

class GetPlaybackProgressUseCase @Inject constructor(
    private val playbackProgressRepository: PlaybackProgressRepository
) {
    suspend operator fun invoke(songId: Long, playlistId: Long): Result<PlaybackProgress?> =
        playbackProgressRepository.getProgress(songId, playlistId)
}
