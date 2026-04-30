package com.rabbithole.musicbbit.presentation.player.components

import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddToPlaylistBottomSheetViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var playlistRepository: PlaylistRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playlistRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load playlists emits Success`() = runTest {
        val playlists = listOf(
            Playlist(id = 1L, name = "Favorites", createdAt = 0L, updatedAt = 0L),
            Playlist(id = 2L, name = "Workout", createdAt = 0L, updatedAt = 0L)
        )
        every { playlistRepository.getAllPlaylists() } returns flowOf(playlists)

        val viewModel = AddToPlaylistBottomSheetViewModel(playlistRepository)

        val state = viewModel.uiState.value as AddToPlaylistUiState.Success
        assertEquals(2, state.playlists.size)
        assertEquals("Favorites", state.playlists[0].name)
    }

    @Test
    fun `load playlists error emits Error`() = runTest {
        val errorFlow = kotlinx.coroutines.flow.flow<List<Playlist>> { throw RuntimeException("DB error") }
        every { playlistRepository.getAllPlaylists() } returns errorFlow

        val viewModel = AddToPlaylistBottomSheetViewModel(playlistRepository)

        assertTrue(viewModel.uiState.value is AddToPlaylistUiState.Error)
    }

    @Test
    fun `retry reloads after error`() = runTest {
        val errorFlow = kotlinx.coroutines.flow.flow<List<Playlist>> { throw RuntimeException("DB error") }
        every { playlistRepository.getAllPlaylists() } returns errorFlow

        val viewModel = AddToPlaylistBottomSheetViewModel(playlistRepository)
        assertTrue(viewModel.uiState.value is AddToPlaylistUiState.Error)

        every { playlistRepository.getAllPlaylists() } returns flowOf(
            listOf(Playlist(id = 1L, name = "Favorites", createdAt = 0L, updatedAt = 0L))
        )
        viewModel.retry()

        val state = viewModel.uiState.value as AddToPlaylistUiState.Success
        assertEquals(1, state.playlists.size)
    }

    @Test
    fun `onPlaylistSelected failure sets error message in Success state`() = runTest {
        val playlists = listOf(Playlist(id = 1L, name = "Favorites", createdAt = 0L, updatedAt = 0L))
        every { playlistRepository.getAllPlaylists() } returns flowOf(playlists)
        coEvery { playlistRepository.addSongToPlaylist(1L, 10L) } returns Result.failure(RuntimeException("Failed"))

        val viewModel = AddToPlaylistBottomSheetViewModel(playlistRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPlaylistSelected(playlistId = 1L, songId = 10L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as AddToPlaylistUiState.Success
        assertEquals(R.string.playlist_error_add_song_failed, state.errorMessageResId)
    }
}
