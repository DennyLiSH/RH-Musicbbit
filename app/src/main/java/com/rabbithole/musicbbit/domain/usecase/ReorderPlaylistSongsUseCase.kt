package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import javax.inject.Inject

class ReorderPlaylistSongsUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, songIds: List<Long>): Result<Unit> = runCatching {
        playlistRepository.reorderPlaylistSongs(playlistId, songIds)
    }
}
