package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylistById(id: Long): Playlist?
    suspend fun createPlaylist(name: String): Result<Long>
    suspend fun updatePlaylist(playlist: Playlist): Result<Unit>
    suspend fun deletePlaylist(playlist: Playlist): Result<Unit>
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long, sortOrder: Int): Result<Unit>
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>): Result<Unit>
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long): Result<Unit>
    suspend fun reorderPlaylistSongs(playlistId: Long, songIds: List<Long>): Result<Unit>
}
