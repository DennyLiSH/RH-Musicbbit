package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.model.PlaylistEntity
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.data.model.SongEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
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
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getPlaylistById(id: Long): Playlist? = withContext(ioDispatcher) {
        playlistDao.getById(id)?.toDomain()
    }

    override suspend fun createPlaylist(name: String): Long = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val entity = PlaylistEntity(
            name = name,
            createdAt = now,
            updatedAt = now
        )
        playlistDao.insert(entity)
    }

    override suspend fun updatePlaylist(playlist: Playlist) = withContext(ioDispatcher) {
        val entity = PlaylistEntity(
            id = playlist.id,
            name = playlist.name,
            createdAt = playlist.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        playlistDao.update(entity)
    }

    override suspend fun deletePlaylist(playlist: Playlist) = withContext(ioDispatcher) {
        val entity = PlaylistEntity(
            id = playlist.id,
            name = playlist.name,
            createdAt = playlist.createdAt,
            updatedAt = playlist.updatedAt
        )
        playlistDao.delete(entity)
    }

    override fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> {
        val playlistFlow = playlistDao.getAll()
            .map { list -> list.find { it.id == playlistId }?.toDomain() }

        val songsFlow = playlistSongDao.getByPlaylistId(playlistId)
            .mapLatest { playlistSongEntities ->
                playlistSongEntities.mapNotNull { ps ->
                    songDao.getById(ps.songId)?.toDomain()
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
    ) = withContext(ioDispatcher) {
        val entity = PlaylistSongEntity(
            playlistId = playlistId,
            songId = songId,
            sortOrder = sortOrder
        )
        playlistSongDao.insert(entity)
    }

    override suspend fun removeSongFromPlaylist(
        playlistId: Long,
        songId: Long
    ) = withContext(ioDispatcher) {
        playlistSongDao.deleteByPlaylistAndSong(playlistId, songId)
    }

    override suspend fun reorderPlaylistSongs(
        playlistId: Long,
        songIds: List<Long>
    ) = withContext(ioDispatcher) {
        playlistSongDao.deleteByPlaylistId(playlistId)
        val entities = songIds.mapIndexed { index, songId ->
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                sortOrder = index
            )
        }
        playlistSongDao.insertAll(entities)
    }

    private fun PlaylistEntity.toDomain(): Playlist {
        return Playlist(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun SongEntity.toDomain(): Song {
        return Song(
            id = id,
            path = path,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            dateAdded = dateAdded,
            coverUri = coverUri
        )
    }
}
