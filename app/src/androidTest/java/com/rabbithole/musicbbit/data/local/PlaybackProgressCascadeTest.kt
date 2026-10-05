package com.rabbithole.musicbbit.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rabbithole.musicbbit.data.local.model.PlaybackProgressEntity
import com.rabbithole.musicbbit.data.local.model.PlaylistEntity
import com.rabbithole.musicbbit.data.local.model.SongEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level verification of the playback_progress FK CASCADE behavior added in
 * migration 10 -> 11. The migration correctness itself is locked in by the JVM
 * Migration10To11Test (non-skippable gate); this suite runs against a freshly built
 * v11 database to confirm the FK actually fires at runtime.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackProgressCascadeTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addMigrations(MIGRATION_10_11).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun dbTest(block: suspend () -> Unit) = runTest(UnconfinedTestDispatcher()) { block() }

    private suspend fun seedSong(): Long =
        db.songDao().insertAll(
            listOf(
                SongEntity(
                    path = "/music/a.mp3",
                    title = "A",
                    artist = null,
                    album = null,
                    durationMs = 1000L,
                    dateAdded = 0L,
                    coverUri = null,
                )
            )
        ).first()

    private suspend fun seedPlaylist(): Long =
        db.playlistDao().insert(
            PlaylistEntity(
                name = "P",
                createdAt = 0L,
                updatedAt = 0L,
            )
        )

    @Test
    fun deletingPlaylist_cascadesProgress() = dbTest {
        val songId = seedSong()
        val playlistId = seedPlaylist()
        db.playbackProgressDao().insert(
            PlaybackProgressEntity(
                songId = songId,
                positionMs = 100L,
                updatedAt = 0L,
                playlistId = playlistId,
            )
        )
        db.playlistDao().delete(PlaylistEntity(id = playlistId, name = "P", createdAt = 0L, updatedAt = 0L))

        val rows = db.playbackProgressDao().getByPlaylistId(playlistId)
        assertTrue("progress rows must be CASCADE-deleted with the playlist", rows.isEmpty())
    }

    @Test
    fun deletingSong_cascadesProgress() = dbTest {
        val songId = seedSong()
        val playlistId = seedPlaylist()
        db.playbackProgressDao().insert(
            PlaybackProgressEntity(
                songId = songId,
                positionMs = 100L,
                updatedAt = 0L,
                playlistId = playlistId,
            )
        )
        db.songDao().delete(
            SongEntity(
                id = songId, path = "/music/a.mp3", title = "A",
                artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null,
            )
        )

        val rows = db.playbackProgressDao().getByPlaylistId(playlistId)
        assertTrue("progress rows must be CASCADE-deleted with the song", rows.isEmpty())
    }

    @Test(expected = SQLiteConstraintException::class)
    fun insertingProgress_forMissingSong_throws() = dbTest {
        val playlistId = seedPlaylist()
        db.playbackProgressDao().insert(
            PlaybackProgressEntity(
                songId = 999L,
                positionMs = 0L,
                updatedAt = 0L,
                playlistId = playlistId,
            )
        )
    }
}