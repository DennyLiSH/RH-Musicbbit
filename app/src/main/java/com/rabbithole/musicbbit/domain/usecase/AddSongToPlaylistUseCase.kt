package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class AddSongToPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playlistSongDao: PlaylistSongDao
) {
    suspend operator fun invoke(playlistId: Long, songId: Long): Result<Unit> = runCatching {
        val currentSongs = playlistSongDao.getByPlaylistId(playlistId).firstOrNull() ?: emptyList()
        val sortOrder = currentSongs.size
        playlistRepository.addSongToPlaylist(playlistId, songId, sortOrder)
    }

    suspend operator fun invoke(playlistId: Long, songIds: List<Long>): Result<Unit> = runCatching {
        playlistRepository.addSongsToPlaylist(playlistId, songIds)
    }
}
