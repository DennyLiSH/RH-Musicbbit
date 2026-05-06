package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val playlistSongDao: PlaylistSongDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAll()
            .flowOn(ioDispatcher)
    }

    override suspend fun getPlaylistById(id: Long): Playlist? = withContext(ioDispatcher) {
        playlistDao.getById(id)
    }

    override suspend fun createPlaylist(name: String): Result<Long> = runCatching {
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val playlist = Playlist(
                name = name,
                createdAt = now,
                updatedAt = now
            )
            val id = playlistDao.insert(playlist)
            Timber.i("Playlist created: id=$id, name=$name")
            id
        }
    }

    override suspend fun updatePlaylist(playlist: Playlist): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            val updated = playlist.copy(updatedAt = System.currentTimeMillis())
            playlistDao.update(updated)
            Timber.i("Playlist updated: id=${playlist.id}, name=${playlist.name}")
        }
    }

    override suspend fun deletePlaylist(playlist: Playlist): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            playlistDao.delete(playlist)
            Timber.i("Playlist deleted: id=${playlist.id}, name=${playlist.name}")
        }
    }

    override fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> {
        return combine(
            playlistDao.getAll(),
            playlistSongDao.getByPlaylistId(playlistId)
        ) { playlists, playlistSongs ->
            playlists.find { it.id == playlistId }?.let { playlist ->
                val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
                val songs = withSongs?.songs ?: emptyList()
                val sortOrderMap = playlistSongs.associate { it.songId to it.sortOrder }
                val sortedSongs = songs.sortedBy { sortOrderMap[it.id] ?: Int.MAX_VALUE }
                PlaylistWithSongs(playlist = playlist, songs = sortedSongs)
            }
        }.flowOn(ioDispatcher)
    }

    override suspend fun addSongToPlaylist(
        playlistId: Long,
        songId: Long
    ): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            val existingSongs = playlistSongDao.getByPlaylistId(playlistId).first()
            val sortOrder = existingSongs.size
            val entity = PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                sortOrder = sortOrder
            )
            playlistSongDao.insert(entity)
            Timber.i("Song added to playlist: playlistId=$playlistId, songId=$songId, sortOrder=$sortOrder")
        }
    }

    override suspend fun addSongsToPlaylist(
        playlistId: Long,
        songIds: List<Long>
    ): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            if (songIds.isEmpty()) return@withContext

            val existingSongs = playlistSongDao.getByPlaylistId(playlistId).first()
            val existingSongIds = existingSongs.map { it.songId }.toSet()

            val newSongIds = songIds.filterNot { it in existingSongIds }
            if (newSongIds.isEmpty()) return@withContext

            val startSortOrder = existingSongs.size
            val entities = newSongIds.mapIndexed { index, songId ->
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = songId,
                    sortOrder = startSortOrder + index
                )
            }
            playlistSongDao.insertAll(entities)
            Timber.i("Songs added to playlist: playlistId=$playlistId, count=${newSongIds.size}")
        }
    }

    override suspend fun removeSongFromPlaylist(
        playlistId: Long,
        songId: Long
    ): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            playlistSongDao.deleteByPlaylistAndSong(playlistId, songId)
            Timber.i("Song removed from playlist: playlistId=$playlistId, songId=$songId")
        }
    }

    override suspend fun reorderPlaylistSongs(
        playlistId: Long,
        songIds: List<Long>
    ): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            playlistSongDao.deleteByPlaylistId(playlistId)
            val entities = songIds.mapIndexed { index, songId ->
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = songId,
                    sortOrder = index
                )
            }
            playlistSongDao.insertAll(entities)
            Timber.i("Playlist reordered: playlistId=$playlistId, songCount=${songIds.size}")
        }
    }
}
