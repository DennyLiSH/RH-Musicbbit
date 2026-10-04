package com.rabbithole.musicbbit.presentation.music

import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import com.rabbithole.musicbbit.presentation.components.ListUiState
import com.rabbithole.musicbbit.presentation.permissions.PermissionStatusMonitor
import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MusicBrowseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var musicRepository: MusicRepository
    private lateinit var scanDirectoryRepository: ScanDirectoryRepository
    private lateinit var permissionStatusMonitor: PermissionStatusMonitor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        musicRepository = mock()
        scanDirectoryRepository = mock()
        val permissionPort = mock<PermissionPort>()
        permissionStatusMonitor = PermissionStatusMonitor(permissionPort)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MusicBrowseViewModel =
        MusicBrowseViewModel(musicRepository, scanDirectoryRepository, permissionStatusMonitor)

    @Test
    fun `load with no scan directories emits Content NoScanDirectory`() = runTest(testDispatcher) {
        whenever(scanDirectoryRepository.getAll()).thenReturn(flowOf(emptyList()))
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ListUiState.Content
        assertTrue(state.data is MusicBrowseData.NoScanDirectory)
    }

    @Test
    fun `load with directories and empty songs emits Content Empty`() = runTest(testDispatcher) {
        whenever(scanDirectoryRepository.getAll()).thenReturn(flowOf(
            listOf(ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L))
        ))
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ListUiState.Content
        assertTrue(state.data is MusicBrowseData.Empty)
    }

    @Test
    fun `load with directories and songs emits Content Songs`() = runTest(testDispatcher) {
        val songs = listOf(
            Song(id = 1L, path = "/a.mp3", title = "Song A", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null),
            Song(id = 2L, path = "/b.mp3", title = "Song B", artist = null, album = null, durationMs = 2000L, dateAdded = 0L, coverUri = null)
        )
        whenever(scanDirectoryRepository.getAll()).thenReturn(flowOf(
            listOf(ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L))
        ))
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(songs))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ListUiState.Content
        val data = state.data as MusicBrowseData.Songs
        assertEquals(2, data.songs.size)
        assertEquals("Song A", data.songs[0].title)
    }

    @Test
    fun `retry reloads after error`() = runTest(testDispatcher) {
        val errorFlow = kotlinx.coroutines.flow.flow<List<ScanDirectory>> { throw RuntimeException("DB error") }
        whenever(scanDirectoryRepository.getAll()).thenReturn(errorFlow)
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ListUiState.Error)

        // Replace stubs with success data before retry.
        whenever(scanDirectoryRepository.getAll()).thenReturn(flowOf(
            listOf(ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L))
        ))
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(
            listOf(Song(id = 1L, path = "/a.mp3", title = "Song A", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null))
        ))
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ListUiState.Content
        val data = state.data as MusicBrowseData.Songs
        assertEquals(1, data.songs.size)
    }
}