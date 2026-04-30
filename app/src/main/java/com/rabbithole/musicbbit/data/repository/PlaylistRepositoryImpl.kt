package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val playlistSongDao: PlaylistSongDao,
    private val songDao: SongDao,
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
        val playlistFlow = playlistDao.getAll()
            .flowOn(ioDispatcher)
            .mapLatest { list -> list.find { it.id == playlistId } }

        val songsFlow = playlistSongDao.getByPlaylistId(playlistId)
            .mapLatest { playlistSongEntities ->
                playlistSongEntities.mapNotNull { ps ->
                    songDao.getById(ps.songId)
                }
            }

        return combine(playlistFlow, songsFlow) { playlist, songs ->
            playlist?.let { PlaylistWithSongs(playlist = it, songs = songs) }
        }.flowOn(ioDispatcher)
    }

    override suspend fun addSongToPlaylist(
        playlistId: Long,
        songId: Long,
        sortOrder: Int
    ): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            val entity = PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                sortOrder = sortOrder
            )
            playlistSongDao.insert(entity)
            Timber.i("Song added to playlist: playlistId=$playlistId, songId=$songId")
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
