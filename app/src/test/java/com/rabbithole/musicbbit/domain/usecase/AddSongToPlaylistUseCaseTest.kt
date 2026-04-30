package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddSongToPlaylistUseCaseTest {

    private val playlistRepository: PlaylistRepository = mockk()
    private lateinit var useCase: AddSongToPlaylistUseCase

    @Before
    fun setup() {
        useCase = AddSongToPlaylistUseCase(playlistRepository)
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `single song delegates to repository`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { playlistRepository.addSongToPlaylist(1L, 30L) } returns Result.success(Unit)

        val result = useCase.invoke(1L, 30L)

        assertTrue(result.isSuccess)
        coVerify { playlistRepository.addSongToPlaylist(1L, 30L) }
    }

    @Test
    fun `single song propagates failure`() = runTest(UnconfinedTestDispatcher()) {
        val error = RuntimeException("DB constraint")
        coEvery { playlistRepository.addSongToPlaylist(1L, 10L) } returns Result.failure(error)

        val result = useCase.invoke(1L, 10L)

        assertTrue(result.isFailure)
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
