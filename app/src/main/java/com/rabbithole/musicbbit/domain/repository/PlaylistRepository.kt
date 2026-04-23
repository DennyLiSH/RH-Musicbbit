package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylistById(id: Long): Playlist?
    suspend fun createPlaylist(name: String): Long
    suspend fun updatePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(playlist: Playlist)
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long, sortOrder: Int)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
    suspend fun reorderPlaylistSongs(playlistId: Long, songIds: List<Long>)
}
