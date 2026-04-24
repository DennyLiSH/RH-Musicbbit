package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Use case that retrieves the playlist with its songs for alarm playback.
 */
class GetPlaylistSongsForAlarmUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long): Result<PlaylistWithSongs?> = runCatching {
        playlistRepository.getPlaylistWithSongs(playlistId).first()
    }
}
