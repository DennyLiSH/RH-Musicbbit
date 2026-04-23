package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import javax.inject.Inject

class CreatePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(name: String): Result<Long> = runCatching {
        require(name.isNotBlank()) { "Playlist name cannot be blank" }
        playlistRepository.createPlaylist(name)
    }
}
