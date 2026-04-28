package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import timber.log.Timber
import javax.inject.Inject

class CreatePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(name: String): Result<Long> {
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("Playlist name cannot be blank"))
        }
        Timber.i("CreatePlaylistUseCase: creating playlist name=$name")
        return playlistRepository.createPlaylist(name)
    }
}
