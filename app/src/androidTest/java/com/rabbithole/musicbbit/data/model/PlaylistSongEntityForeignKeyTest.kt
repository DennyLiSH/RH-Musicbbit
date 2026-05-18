package com.rabbithole.musicbbit.data.model

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rabbithole.musicbbit.data.local.AppDatabase
import com.rabbithole.musicbbit.data.local.model.PlaylistEntity
import com.rabbithole.musicbbit.data.local.model.SongEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistSongEntityForeignKeyTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun dbTest(block: suspend () -> Unit) = runTest(UnconfinedTestDispatcher()) {
        block()
    }

    private suspend fun createPlaylist(name: String): Long {
        return db.playlistDao().insert(
            PlaylistEntity(
                name = name,
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L
            )
        )
    }

    private suspend fun createSong(title: String): Long {
        return db.songDao().insert(
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
    fun deletePlaylist_cascadeDeletesPlaylistSongs() = dbTest {
        val playlistId = createPlaylist("Test Playlist")
        val songId = createSong("Test Song")
        val playlistSong = PlaylistSongEntity(
            playlistId = playlistId,
            songId = songId,
            sortOrder = 1
        )
        db.playlistSongDao().insert(playlistSong)

        val playlist = db.playlistDao().getById(playlistId)!!
        db.playlistDao().delete(playlist)

        val result = db.playlistSongDao().getByPlaylistId(playlistId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun deleteSong_cascadeDeletesPlaylistSongs() = dbTest {
        val playlistId = createPlaylist("Test Playlist")
        val songId = createSong("Test Song")
        val playlistSong = PlaylistSongEntity(
            playlistId = playlistId,
            songId = songId,
            sortOrder = 1
        )
        db.playlistSongDao().insert(playlistSong)

        val song = db.songDao().getById(songId)!!
        db.songDao().delete(song)

        val result = db.playlistSongDao().getByPlaylistId(playlistId).first()
        assertTrue(result.isEmpty())
    }

    @Test(expected = SQLiteConstraintException::class)
    fun insertPlaylistSongWithInvalidPlaylistId_throws() = dbTest {
        val songId = createSong("Test Song")
        val playlistSong = PlaylistSongEntity(
            playlistId = 999L,
            songId = songId,
            sortOrder = 1
        )
        db.playlistSongDao().insert(playlistSong)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun insertPlaylistSongWithInvalidSongId_throws() = dbTest {
        val playlistId = createPlaylist("Test Playlist")
        val playlistSong = PlaylistSongEntity(
            playlistId = playlistId,
            songId = 999L,
            sortOrder = 1
        )
        db.playlistSongDao().insert(playlistSong)
    }
}
