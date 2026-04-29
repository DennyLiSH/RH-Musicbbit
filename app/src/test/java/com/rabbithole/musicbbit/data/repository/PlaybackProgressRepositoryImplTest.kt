package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.PlaybackProgressDao
import com.rabbithole.musicbbit.data.model.PlaybackProgressEntity
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressRepositoryImplTest {

    private val playbackProgressDao: PlaybackProgressDao = mockk()
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
        coEvery { playbackProgressDao.insert(any()) } returns Unit

        val result = repository.saveProgress(progress)

        assertTrue(result.isSuccess)
        coVerify { playbackProgressDao.insert(match { it.songId == 5L && it.positionMs == 60000L && it.playlistId == 2L }) }
    }

    @Test
    fun `getProgress returns mapped domain object`() = runTest(testDispatcher) {
        val entity = progressEntity(songId = 3L, positionMs = 45000L, playlistId = 7L)
        coEvery { playbackProgressDao.getBySongIdAndPlaylistId(3L, 7L) } returns entity

        val result = repository.getProgress(3L, 7L)

        assertTrue(result.isSuccess)
        val progress = result.getOrNull()
        assertEquals(3L, progress!!.songId)
        assertEquals(45000L, progress.positionMs)
        assertEquals(7L, progress.playlistId)
    }

    @Test
    fun `getProgress returns null when not found`() = runTest(testDispatcher) {
        coEvery { playbackProgressDao.getBySongIdAndPlaylistId(99L, 88L) } returns null

        val result = repository.getProgress(99L, 88L)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `deleteProgress delegates to DAO`() = runTest(testDispatcher) {
        coEvery { playbackProgressDao.deleteBySongIdAndPlaylistId(5L, 10L) } returns Unit

        val result = repository.deleteProgress(5L, 10L)

        assertTrue(result.isSuccess)
        coVerify { playbackProgressDao.deleteBySongIdAndPlaylistId(5L, 10L) }
    }

    @Test
    fun `deleteAllProgressForPlaylist delegates to DAO`() = runTest(testDispatcher) {
        coEvery { playbackProgressDao.deleteByPlaylistId(20L) } returns Unit

        val result = repository.deleteAllProgressForPlaylist(20L)

        assertTrue(result.isSuccess)
        coVerify { playbackProgressDao.deleteByPlaylistId(20L) }
    }
}
