package com.rabbithole.musicbbit.presentation.music

import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicBrowseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var musicRepository: MusicRepository
    private lateinit var scanDirectoryRepository: ScanDirectoryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        musicRepository = mockk(relaxed = true)
        scanDirectoryRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load with no scan directories emits NoScanDirectory`() = runTest(testDispatcher) {
        every { scanDirectoryRepository.getAll() } returns flowOf(emptyList())
        every { musicRepository.getAllSongs() } returns flowOf(emptyList())

        val viewModel = MusicBrowseViewModel(musicRepository, scanDirectoryRepository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is MusicUiState.NoScanDirectory)
    }

    @Test
    fun `load with directories and empty songs emits Empty`() = runTest(testDispatcher) {
        every { scanDirectoryRepository.getAll() } returns flowOf(
            listOf(ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L))
        )
        every { musicRepository.getAllSongs() } returns flowOf(emptyList())

        val viewModel = MusicBrowseViewModel(musicRepository, scanDirectoryRepository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is MusicUiState.Empty)
    }

    @Test
    fun `load with directories and songs emits Success`() = runTest(testDispatcher) {
        val songs = listOf(
            Song(id = 1L, path = "/a.mp3", title = "Song A", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null),
            Song(id = 2L, path = "/b.mp3", title = "Song B", artist = null, album = null, durationMs = 2000L, dateAdded = 0L, coverUri = null)
        )
        every { scanDirectoryRepository.getAll() } returns flowOf(
            listOf(ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L))
        )
        every { musicRepository.getAllSongs() } returns flowOf(songs)

        val viewModel = MusicBrowseViewModel(musicRepository, scanDirectoryRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MusicUiState.Success
        assertEquals(2, state.songs.size)
        assertEquals("Song A", state.songs[0].title)
    }

    @Test
    fun `retry reloads after error`() = runTest(testDispatcher) {
        val errorFlow = kotlinx.coroutines.flow.flow<List<ScanDirectory>> { throw RuntimeException("DB error") }
        every { scanDirectoryRepository.getAll() } returns errorFlow
        every { musicRepository.getAllSongs() } returns flowOf(emptyList())

        val viewModel = MusicBrowseViewModel(musicRepository, scanDirectoryRepository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is MusicUiState.Error)

        every { scanDirectoryRepository.getAll() } returns flowOf(
            listOf(ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L))
        )
        every { musicRepository.getAllSongs() } returns flowOf(
            listOf(Song(id = 1L, path = "/a.mp3", title = "Song A", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null))
        )
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value as MusicUiState.Success
        assertEquals(1, state.songs.size)
    }
}
