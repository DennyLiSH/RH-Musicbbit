package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject

class AddSongToPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playlistSongDao: PlaylistSongDao
) {
    suspend operator fun invoke(playlistId: Long, songId: Long): Result<Unit> = runCatching {
        val currentSongs = playlistSongDao.getByPlaylistId(playlistId).firstOrNull() ?: emptyList()
        currentSongs.size
    }.mapCatching { sortOrder ->
        Timber.i("AddSongToPlaylistUseCase: adding songId=$songId to playlistId=$playlistId")
        playlistRepository.addSongToPlaylist(playlistId, songId, sortOrder).getOrThrow()
    }

    suspend operator fun invoke(playlistId: Long, songIds: List<Long>): Result<Unit> = runCatching {
        Timber.i("AddSongToPlaylistUseCase: adding ${songIds.size} songs to playlistId=$playlistId")
        playlistRepository.addSongsToPlaylist(playlistId, songIds).getOrThrow()
    }
}
