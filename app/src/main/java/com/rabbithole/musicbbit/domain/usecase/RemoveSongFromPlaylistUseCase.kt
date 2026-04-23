package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import javax.inject.Inject

class RemoveSongFromPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, songId: Long): Result<Unit> = runCatching {
        playlistRepository.removeSongFromPlaylist(playlistId, songId)
    }
}
