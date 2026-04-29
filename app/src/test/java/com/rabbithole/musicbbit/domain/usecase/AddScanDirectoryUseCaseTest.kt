package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
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
class AddScanDirectoryUseCaseTest {

    private val scanDirectoryRepository: ScanDirectoryRepository = mockk()
    private val musicRepository: MusicRepository = mockk()
    private lateinit var useCase: AddScanDirectoryUseCase

    @Before
    fun setup() {
        useCase = AddScanDirectoryUseCase(scanDirectoryRepository, musicRepository)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun scanDirectory(
        id: Long = 0L,
        path: String = "/storage/Music",
        name: String = "Music",
        addedAt: Long = 1000L
    ) = ScanDirectory(id = id, path = path, name = name, addedAt = addedAt)

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `success triggers refreshSongs`() = runTest(UnconfinedTestDispatcher()) {
        val dir = scanDirectory(path = "/storage/Music")
        coEvery { scanDirectoryRepository.add(dir) } returns Result.success(1L)
        coEvery { musicRepository.refreshSongs() } returns Result.success(Unit)

        val result = useCase.invoke(dir)

        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrNull())
        coVerify { musicRepository.refreshSongs() }
    }

    @Test
    fun `failure skips refreshSongs`() = runTest(UnconfinedTestDispatcher()) {
        val dir = scanDirectory(path = "/storage/Music")
        coEvery { scanDirectoryRepository.add(dir) } returns Result.failure(RuntimeException("error"))

        val result = useCase.invoke(dir)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { musicRepository.refreshSongs() }
    }

    @Test
    fun `returns the result from repository`() = runTest(UnconfinedTestDispatcher()) {
        val dir = scanDirectory(path = "/storage/Downloads")
        coEvery { scanDirectoryRepository.add(dir) } returns Result.success(99L)
        coEvery { musicRepository.refreshSongs() } returns Result.success(Unit)

        val result = useCase.invoke(dir)

        assertEquals(99L, result.getOrNull())
    }
}
