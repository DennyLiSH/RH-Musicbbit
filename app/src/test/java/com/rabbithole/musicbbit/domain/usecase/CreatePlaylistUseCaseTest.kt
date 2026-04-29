package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePlaylistUseCaseTest {

    private val playlistRepository: PlaylistRepository = mockk()
    private lateinit var useCase: CreatePlaylistUseCase

    @Before
    fun setup() {
        useCase = CreatePlaylistUseCase(playlistRepository)
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `blank name returns failure`() = runTest(UnconfinedTestDispatcher()) {
        val result = useCase.invoke("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `valid name delegates to repository`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { playlistRepository.createPlaylist("Morning Vibes") } returns Result.success(42L)

        val result = useCase.invoke("Morning Vibes")

        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrNull())
        coVerify { playlistRepository.createPlaylist("Morning Vibes") }
    }

    @Test
    fun `repository failure propagates`() = runTest(UnconfinedTestDispatcher()) {
        val error = RuntimeException("DB error")
        coEvery { playlistRepository.createPlaylist("Test") } returns Result.failure(error)

        val result = useCase.invoke("Test")

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }
}
