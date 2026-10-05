package com.rabbithole.musicbbit.data.local.dao

import com.rabbithole.musicbbit.data.local.model.PlaybackProgressEntity
import com.rabbithole.musicbbit.data.local.model.PlaylistEntity
import com.rabbithole.musicbbit.data.local.model.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackProgressDaoTest : DatabaseTest() {

    private val dao by lazy { db.playbackProgressDao() }

    // FK (migration 10->11) requires parent rows; same seeding recipe as
    // PlaybackProgressCascadeTest. Auto-generated ids are used so the fixtures
    // never assume ids start at 1.
    private suspend fun seedSong(path: String, title: String): Long =
        db.songDao().insertAll(
            listOf(
                SongEntity(
                    path = path,
                    title = title,
                    artist = null,
                    album = null,
                    durationMs = 1000L,
                    dateAdded = 0L,
                    coverUri = null,
                )
            )
        ).first()

    private suspend fun seedPlaylist(name: String): Long =
        db.playlistDao().insert(
            PlaylistEntity(
                name = name,
                createdAt = 0L,
                updatedAt = 0L,
            )
        )

    @Test
    fun insert_and_getBySongIdAndPlaylistId() = dbTest {
        val songId = seedSong("/music/a.mp3", "A")
        val playlistId = seedPlaylist("P")
        val progress = PlaybackProgressEntity(
            songId = songId,
            playlistId = playlistId,
            positionMs = 30_000L,
            updatedAt = 1_700_000_000_000L
        )

        dao.insert(progress)
        val result = dao.getBySongIdAndPlaylistId(songId = songId, playlistId = playlistId)

        assertNotNull(result)
        assertEquals(songId, result?.songId)
        assertEquals(playlistId, result?.playlistId)
        assertEquals(30_000L, result?.positionMs)
    }

    @Test
    fun getBySongIdAndPlaylistId_returnsNull_whenNotExists() = dbTest {
        val result = dao.getBySongIdAndPlaylistId(songId = 999L, playlistId = 999L)

        assertNull(result)
    }

    @Test
    fun deleteByPlaylistId_removesBatch() = dbTest {
        val songA = seedSong("/music/a.mp3", "A")
        val songB = seedSong("/music/b.mp3", "B")
        val songC = seedSong("/music/c.mp3", "C")
        val playlistX = seedPlaylist("PX")
        val playlistY = seedPlaylist("PY")
        val progress1 = PlaybackProgressEntity(
            songId = songA,
            playlistId = playlistX,
            positionMs = 30_000L,
            updatedAt = 1_700_000_000_000L
        )
        val progress2 = PlaybackProgressEntity(
            songId = songB,
            playlistId = playlistX,
            positionMs = 60_000L,
            updatedAt = 1_700_000_001_000L
        )
        val progress3 = PlaybackProgressEntity(
            songId = songC,
            playlistId = playlistY,
            positionMs = 90_000L,
            updatedAt = 1_700_000_002_000L
        )
        dao.insert(progress1)
        dao.insert(progress2)
        dao.insert(progress3)

        dao.deleteByPlaylistId(playlistId = playlistX)
        val result1 = dao.getBySongIdAndPlaylistId(songId = songA, playlistId = playlistX)
        val result2 = dao.getBySongIdAndPlaylistId(songId = songB, playlistId = playlistX)
        val result3 = dao.getBySongIdAndPlaylistId(songId = songC, playlistId = playlistY)

        assertNull(result1)
        assertNull(result2)
        assertNotNull(result3)
    }

    @Test
    fun getByPlaylistId_returnsOrderedResults() = dbTest {
        val songA = seedSong("/music/a.mp3", "A")
        val songB = seedSong("/music/b.mp3", "B")
        val songC = seedSong("/music/c.mp3", "C")
        val playlistX = seedPlaylist("PX")
        val playlistY = seedPlaylist("PY")
        val progress1 = PlaybackProgressEntity(
            songId = songA,
            playlistId = playlistX,
            positionMs = 30_000L,
            updatedAt = 1_700_000_001_000L
        )
        val progress2 = PlaybackProgressEntity(
            songId = songB,
            playlistId = playlistX,
            positionMs = 60_000L,
            updatedAt = 1_700_000_002_000L
        )
        val progress3 = PlaybackProgressEntity(
            songId = songC,
            playlistId = playlistY,
            positionMs = 90_000L,
            updatedAt = 1_700_000_003_000L
        )
        dao.insert(progress1)
        dao.insert(progress2)
        dao.insert(progress3)

        val result = dao.getByPlaylistId(playlistId = playlistX)

        assertEquals(2, result.size)
        assertEquals(songB, result[0].songId)
        assertEquals(songA, result[1].songId)
    }
}