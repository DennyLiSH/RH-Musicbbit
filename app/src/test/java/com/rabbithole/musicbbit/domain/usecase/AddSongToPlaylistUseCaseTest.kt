package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddSongToPlaylistUseCaseTest {

    private val playlistRepository: PlaylistRepository = mockk()
    private val playlistSongDao: PlaylistSongDao = mockk()
    private lateinit var useCase: AddSongToPlaylistUseCase

    @Before
    fun setup() {
        useCase = AddSongToPlaylistUseCase(playlistRepository, playlistSongDao)
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `single song computes sortOrder from current list size`() = runTest(UnconfinedTestDispatcher()) {
        val existingSongs = listOf(
            PlaylistSongEntity(playlistId = 1L, songId = 10L, sortOrder = 0),
            PlaylistSongEntity(playlistId = 1L, songId = 20L, sortOrder = 1)
        )
        every { playlistSongDao.getByPlaylistId(1L) } returns flowOf(existingSongs)
        coEvery { playlistRepository.addSongToPlaylist(1L, 30L, 2) } returns Result.success(Unit)

        val result = useCase.invoke(1L, 30L)

        assertTrue(result.isSuccess)
        coVerify { playlistRepository.addSongToPlaylist(1L, 30L, 2) }
    }

    @Test
    fun `single song with empty list uses sortOrder 0`() = runTest(UnconfinedTestDispatcher()) {
        every { playlistSongDao.getByPlaylistId(1L) } returns flowOf(emptyList())
        coEvery { playlistRepository.addSongToPlaylist(1L, 10L, 0) } returns Result.success(Unit)

        val result = useCase.invoke(1L, 10L)

        assertTrue(result.isSuccess)
        coVerify { playlistRepository.addSongToPlaylist(1L, 10L, 0) }
    }

    @Test
    fun `batch add delegates to repository addSongsToPlaylist`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { playlistRepository.addSongsToPlaylist(1L, listOf(10L, 20L)) } returns Result.success(Unit)

        val result = useCase.invoke(1L, listOf(10L, 20L))

        assertTrue(result.isSuccess)
        coVerify { playlistRepository.addSongsToPlaylist(1L, listOf(10L, 20L)) }
    }

    @Test
    fun `batch add propagates failure`() = runTest(UnconfinedTestDispatcher()) {
        val error = RuntimeException("DB constraint")
        coEvery { playlistRepository.addSongsToPlaylist(1L, listOf(10L)) } returns Result.failure(error)

        val result = useCase.invoke(1L, listOf(10L))

        assertTrue(result.isFailure)
    }
}
