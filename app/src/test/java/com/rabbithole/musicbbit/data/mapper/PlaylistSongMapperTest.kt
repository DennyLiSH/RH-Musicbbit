package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSongMapperTest {

    private val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)

    private val song1 = Song(id = 10L, path = "/a.mp3", title = "A", artist = null, album = null, durationMs = 100L, dateAdded = 0L, coverUri = null)
    private val song2 = Song(id = 20L, path = "/b.mp3", title = "B", artist = null, album = null, durationMs = 100L, dateAdded = 0L, coverUri = null)
    private val song3 = Song(id = 30L, path = "/c.mp3", title = "C", artist = null, album = null, durationMs = 100L, dateAdded = 0L, coverUri = null)

    @Test
    fun `toPlaylistWithSongs sorts by sortOrder`() {
        val songs = listOf(song1, song2, song3)
        val sortOrders = listOf(
            PlaylistSongEntity(playlistId = 1L, songId = 20L, sortOrder = 0),
            PlaylistSongEntity(playlistId = 1L, songId = 10L, sortOrder = 1),
            PlaylistSongEntity(playlistId = 1L, songId = 30L, sortOrder = 2)
        )

        val result = PlaylistSongMapper.toPlaylistWithSongs(playlist, songs, sortOrders)

        assertEquals(listOf(song2, song1, song3), result.songs)
        assertEquals(playlist, result.playlist)
    }

    @Test
    fun `toPlaylistWithSongs puts missing sortOrder songs at end`() {
        val songs = listOf(song1, song2, song3)
        val sortOrders = listOf(
            PlaylistSongEntity(playlistId = 1L, songId = 20L, sortOrder = 0)
        )

        val result = PlaylistSongMapper.toPlaylistWithSongs(playlist, songs, sortOrders)

        assertEquals(song2, result.songs.first())
        // song1 and song3 have no sortOrder, they go at the end (order stable among themselves)
        assertEquals(3, result.songs.size)
    }

    @Test
    fun `toPlaylistWithSongs handles empty songs`() {
        val result = PlaylistSongMapper.toPlaylistWithSongs(playlist, emptyList(), emptyList())

        assertEquals(emptyList<Song>(), result.songs)
        assertEquals(playlist, result.playlist)
    }

    @Test
    fun `toPlaylistWithSongs handles empty sortOrders`() {
        val songs = listOf(song1, song2)

        val result = PlaylistSongMapper.toPlaylistWithSongs(playlist, songs, emptyList())

        // All songs have no sortOrder, all get Int.MAX_VALUE, preserve input order
        assertEquals(listOf(song1, song2), result.songs)
    }
}
