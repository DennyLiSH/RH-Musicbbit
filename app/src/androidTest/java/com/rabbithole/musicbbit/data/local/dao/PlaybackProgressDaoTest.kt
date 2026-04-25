package com.rabbithole.musicbbit.data.local.dao

import com.rabbithole.musicbbit.data.model.PlaybackProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressDaoTest : DatabaseTest() {

    private val dao by lazy { db.playbackProgressDao() }

    @Test
    fun insert_and_getBySongIdAndPlaylistId() = dbTest {
        val progress = PlaybackProgressEntity(
            songId = 1L,
            playlistId = 2L,
            positionMs = 30_000L,
            updatedAt = 1_700_000_000_000L
        )

        dao.insert(progress)
        val result = dao.getBySongIdAndPlaylistId(songId = 1L, playlistId = 2L)

        assertNotNull(result)
        assertEquals(1L, result?.songId)
        assertEquals(2L, result?.playlistId)
        assertEquals(30_000L, result?.positionMs)
    }

    @Test
    fun getBySongIdAndPlaylistId_returnsNull_whenNotExists() = dbTest {
        val result = dao.getBySongIdAndPlaylistId(songId = 999L, playlistId = 999L)

        assertNull(result)
    }

    @Test
    fun deleteBySongIdAndPlaylistId_removesExact() = dbTest {
        val progress1 = PlaybackProgressEntity(
            songId = 1L,
            playlistId = 2L,
            positionMs = 30_000L,
            updatedAt = 1_700_000_000_000L
        )
        val progress2 = PlaybackProgressEntity(
            songId = 3L,
            playlistId = 2L,
            positionMs = 60_000L,
            updatedAt = 1_700_000_001_000L
        )
        dao.insert(progress1)
        dao.insert(progress2)

        dao.deleteBySongIdAndPlaylistId(songId = 1L, playlistId = 2L)
        val result = dao.getBySongIdAndPlaylistId(songId = 1L, playlistId = 2L)
        val remaining = dao.getBySongIdAndPlaylistId(songId = 3L, playlistId = 2L)

        assertNull(result)
        assertNotNull(remaining)
    }

    @Test
    fun deleteByPlaylistId_removesBatch() = dbTest {
        val progress1 = PlaybackProgressEntity(
            songId = 1L,
            playlistId = 2L,
            positionMs = 30_000L,
            updatedAt = 1_700_000_000_000L
        )
        val progress2 = PlaybackProgressEntity(
            songId = 3L,
            playlistId = 2L,
            positionMs = 60_000L,
            updatedAt = 1_700_000_001_000L
        )
        val progress3 = PlaybackProgressEntity(
            songId = 4L,
            playlistId = 5L,
            positionMs = 90_000L,
            updatedAt = 1_700_000_002_000L
        )
        dao.insert(progress1)
        dao.insert(progress2)
        dao.insert(progress3)

        dao.deleteByPlaylistId(playlistId = 2L)
        val result1 = dao.getBySongIdAndPlaylistId(songId = 1L, playlistId = 2L)
        val result2 = dao.getBySongIdAndPlaylistId(songId = 3L, playlistId = 2L)
        val result3 = dao.getBySongIdAndPlaylistId(songId = 4L, playlistId = 5L)

        assertNull(result1)
        assertNull(result2)
        assertNotNull(result3)
    }

    @Test
    fun deleteAll_clearsAll() = dbTest {
        val progress1 = PlaybackProgressEntity(
            songId = 1L,
            playlistId = 2L,
            positionMs = 30_000L,
            updatedAt = 1_700_000_000_000L
        )
        val progress2 = PlaybackProgressEntity(
            songId = 3L,
            playlistId = 4L,
            positionMs = 60_000L,
            updatedAt = 1_700_000_001_000L
        )
        dao.insert(progress1)
        dao.insert(progress2)

        dao.deleteAll()
        val result1 = dao.getBySongIdAndPlaylistId(songId = 1L, playlistId = 2L)
        val result2 = dao.getBySongIdAndPlaylistId(songId = 3L, playlistId = 4L)

        assertNull(result1)
        assertNull(result2)
    }

    @Test
    fun getByPlaylistId_returnsOrderedResults() = dbTest {
        val progress1 = PlaybackProgressEntity(
            songId = 1L,
            playlistId = 2L,
            positionMs = 30_000L,
            updatedAt = 1_700_000_001_000L
        )
        val progress2 = PlaybackProgressEntity(
            songId = 3L,
            playlistId = 2L,
            positionMs = 60_000L,
            updatedAt = 1_700_000_002_000L
        )
        val progress3 = PlaybackProgressEntity(
            songId = 4L,
            playlistId = 5L,
            positionMs = 90_000L,
            updatedAt = 1_700_000_003_000L
        )
        dao.insert(progress1)
        dao.insert(progress2)
        dao.insert(progress3)

        val result = dao.getByPlaylistId(playlistId = 2L)

        assertEquals(2, result.size)
        assertEquals(3L, result[0].songId)
        assertEquals(1L, result[1].songId)
    }
}
