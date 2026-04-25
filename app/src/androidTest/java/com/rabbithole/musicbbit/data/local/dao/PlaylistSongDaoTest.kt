package com.rabbithole.musicbbit.data.local.dao

import com.rabbithole.musicbbit.data.model.PlaylistEntity
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.data.model.SongEntity
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSongDaoTest : DatabaseTest() {

    private val playlistSongDao by lazy { db.playlistSongDao() }
    private val playlistDao by lazy { db.playlistDao() }
    private val songDao by lazy { db.songDao() }

    private suspend fun createPlaylist(name: String): Long {
        return playlistDao.insert(
            PlaylistEntity(
                name = name,
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L
            )
        )
    }

    private suspend fun createSong(title: String): Long {
        return songDao.insert(
            SongEntity(
                path = "/music/$title.mp3",
                title = title,
                artist = "Artist",
                album = "Album",
                durationMs = 180_000L,
                dateAdded = 1_700_000_000_000L,
                coverUri = null
            )
        )
    }

    @Test
    fun insert_and_getByPlaylistId() = dbTest {
        val playlistId = createPlaylist("Test Playlist")
        val songId = createSong("Song One")
        val playlistSong = PlaylistSongEntity(
            playlistId = playlistId,
            songId = songId,
            sortOrder = 1
        )

        playlistSongDao.insert(playlistSong)
        val result = playlistSongDao.getByPlaylistId(playlistId).first()

        assertEquals(1, result.size)
        assertEquals(playlistId, result[0].playlistId)
        assertEquals(songId, result[0].songId)
        assertEquals(1, result[0].sortOrder)
    }

    @Test
    fun insertAll_batchInsert() = dbTest {
        val playlistId = createPlaylist("Test Playlist")
        val songId1 = createSong("Song One")
        val songId2 = createSong("Song Two")
        val playlistSongs = listOf(
            PlaylistSongEntity(playlistId = playlistId, songId = songId1, sortOrder = 1),
            PlaylistSongEntity(playlistId = playlistId, songId = songId2, sortOrder = 2)
        )

        playlistSongDao.insertAll(playlistSongs)
        val result = playlistSongDao.getByPlaylistId(playlistId).first()

        assertEquals(2, result.size)
    }

    @Test
    fun deleteByPlaylistId_cascadeRemoves() = dbTest {
        val playlistId = createPlaylist("Test Playlist")
        val songId1 = createSong("Song One")
        val songId2 = createSong("Song Two")
        playlistSongDao.insertAll(
            listOf(
                PlaylistSongEntity(playlistId = playlistId, songId = songId1, sortOrder = 1),
                PlaylistSongEntity(playlistId = playlistId, songId = songId2, sortOrder = 2)
            )
        )

        playlistSongDao.deleteByPlaylistId(playlistId)
        val result = playlistSongDao.getByPlaylistId(playlistId).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun deleteByPlaylistAndSong_removesExact() = dbTest {
        val playlistId = createPlaylist("Test Playlist")
        val songId1 = createSong("Song One")
        val songId2 = createSong("Song Two")
        playlistSongDao.insertAll(
            listOf(
                PlaylistSongEntity(playlistId = playlistId, songId = songId1, sortOrder = 1),
                PlaylistSongEntity(playlistId = playlistId, songId = songId2, sortOrder = 2)
            )
        )

        playlistSongDao.deleteByPlaylistAndSong(playlistId, songId1)
        val result = playlistSongDao.getByPlaylistId(playlistId).first()

        assertEquals(1, result.size)
        assertEquals(songId2, result[0].songId)
    }
}
