package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.PlaybackProgressDao
import com.rabbithole.musicbbit.data.local.model.PlaybackProgressEntity
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressRepositoryImplTest {

    private val playbackProgressDao: PlaybackProgressDao = mock()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: PlaybackProgressRepositoryImpl

    @Before
    fun setup() {
        repository = PlaybackProgressRepositoryImpl(playbackProgressDao, testDispatcher)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun progressEntity(
        songId: Long = 1L,
        positionMs: Long = 30000L,
        updatedAt: Long = 1000L,
        playlistId: Long = 10L
    ) = PlaybackProgressEntity(
        songId = songId,
        positionMs = positionMs,
        updatedAt = updatedAt,
        playlistId = playlistId
    )

    private fun progressDomain(
        songId: Long = 1L,
        positionMs: Long = 30000L,
        updatedAt: Long = 1000L,
        playlistId: Long = 10L
    ) = PlaybackProgress(
        songId = songId,
        positionMs = positionMs,
        updatedAt = updatedAt,
        playlistId = playlistId
    )

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `saveProgress inserts entity and returns success`() = runTest(testDispatcher) {
        val progress = progressDomain(songId = 5L, positionMs = 60000L, playlistId = 2L)
        wheneverBlocking { playbackProgressDao.insert(any()) } doReturn Unit

        val result = repository.saveProgress(progress)

        assertTrue(result.isSuccess)
        verifyBlocking(playbackProgressDao) {
            insert(argThat { songId == 5L && positionMs == 60000L && playlistId == 2L })
        }
    }

    @Test
    fun `getProgress returns mapped domain object`() = runTest(testDispatcher) {
        val entity = progressEntity(songId = 3L, positionMs = 45000L, playlistId = 7L)
        wheneverBlocking { playbackProgressDao.getBySongIdAndPlaylistId(3L, 7L) } doReturn entity

        val result = repository.getProgress(3L, 7L)

        assertTrue(result.isSuccess)
        val progress = result.getOrNull()
        assertEquals(3L, progress!!.songId)
        assertEquals(45000L, progress.positionMs)
        assertEquals(7L, progress.playlistId)
    }

    @Test
    fun `getProgress returns null when not found`() = runTest(testDispatcher) {
        wheneverBlocking { playbackProgressDao.getBySongIdAndPlaylistId(99L, 88L) } doReturn null

        val result = repository.getProgress(99L, 88L)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `deleteProgress delegates to DAO`() = runTest(testDispatcher) {
        wheneverBlocking { playbackProgressDao.deleteBySongIdAndPlaylistId(5L, 10L) } doReturn Unit

        val result = repository.deleteProgress(5L, 10L)

        assertTrue(result.isSuccess)
        verifyBlocking(playbackProgressDao) { deleteBySongIdAndPlaylistId(5L, 10L) }
    }

    @Test
    fun `deleteAllProgressForPlaylist delegates to DAO`() = runTest(testDispatcher) {
        wheneverBlocking { playbackProgressDao.deleteByPlaylistId(20L) } doReturn Unit

        val result = repository.deleteAllProgressForPlaylist(20L)

        assertTrue(result.isSuccess)
        verifyBlocking(playbackProgressDao) { deleteByPlaylistId(20L) }
    }

    @Test
    fun `getProgressForPlaylist returns mapped domain list`() = runTest(testDispatcher) {
        val entities = listOf(
            progressEntity(songId = 1L, positionMs = 10000L, playlistId = 5L),
            progressEntity(songId = 2L, positionMs = 20000L, playlistId = 5L)
        )
        wheneverBlocking { playbackProgressDao.getByPlaylistId(5L) } doReturn entities

        val result = repository.getProgressForPlaylist(5L)

        assertTrue(result.isSuccess)
        val list = result.getOrNull()!!
        assertEquals(2, list.size)
        assertEquals(1L, list[0].songId)
        assertEquals(10000L, list[0].positionMs)
        assertEquals(2L, list[1].songId)
        assertEquals(20000L, list[1].positionMs)
    }
}
