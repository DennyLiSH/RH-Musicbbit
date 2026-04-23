package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import javax.inject.Inject

class UpdatePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(playlist: Playlist): Result<Unit> = runCatching {
        playlistRepository.updatePlaylist(playlist)
    }
}
