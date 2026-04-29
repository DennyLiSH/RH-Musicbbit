package com.rabbithole.musicbbit.presentation.playlist

import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.domain.usecase.CreatePlaylistUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var createPlaylistUseCase: CreatePlaylistUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playlistRepository = mockk(relaxed = true)
        createPlaylistUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState is Loading`() = runTest(testDispatcher) {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())

        val viewModel = PlaylistListViewModel(playlistRepository, createPlaylistUseCase)

        assertTrue(viewModel.uiState.value is PlaylistListUiState.Loading)
    }

    @Test
    fun `uiState becomes Success with playlists when repository emits data`() = runTest(testDispatcher) {
        val playlists = listOf(
            Playlist(id = 1L, name = "Favorites", createdAt = 0L, updatedAt = 0L),
            Playlist(id = 2L, name = "Workout", createdAt = 0L, updatedAt = 0L)
        )
        every { playlistRepository.getAllPlaylists() } returns flowOf(playlists)

        val viewModel = PlaylistListViewModel(playlistRepository, createPlaylistUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlaylistListUiState.Success
        assertEquals(2, state.playlists.size)
        assertEquals("Favorites", state.playlists[0].name)
        assertEquals("Workout", state.playlists[1].name)
    }

    @Test
    fun `uiState becomes Success with empty list when repository emits empty`() = runTest(testDispatcher) {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())

        val viewModel = PlaylistListViewModel(playlistRepository, createPlaylistUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlaylistListUiState.Success
        assertTrue(state.playlists.isEmpty())
    }

    @Test
    fun `create playlist success does not set error`() = runTest(testDispatcher) {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())
        coEvery { createPlaylistUseCase("New") } returns Result.success(3L)

        val viewModel = PlaylistListViewModel(playlistRepository, createPlaylistUseCase)
        advanceUntilIdle()

        viewModel.onAction(PlaylistListAction.OnCreatePlaylist("New"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlaylistListUiState.Success
        assertNull(state.errorMessageResId)
    }

    @Test
    fun `create playlist failure sets error in Success state`() = runTest(testDispatcher) {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())
        coEvery { createPlaylistUseCase("New") } coAnswers { Result.failure(RuntimeException("Failed")) }

        val viewModel = PlaylistListViewModel(playlistRepository, createPlaylistUseCase)
        advanceUntilIdle()

        viewModel.onAction(PlaylistListAction.OnCreatePlaylist("New"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlaylistListUiState.Success
        assertEquals(com.rabbithole.musicbbit.R.string.playlist_error_add_song_failed, state.errorMessageResId)
    }

    @Test
    fun `delete playlist calls repository deletePlaylist`() = runTest(testDispatcher) {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())
        coEvery { playlistRepository.deletePlaylist(any()) } returns Result.success(Unit)

        val viewModel = PlaylistListViewModel(playlistRepository, createPlaylistUseCase)
        advanceUntilIdle()

        val playlist = Playlist(id = 1L, name = "ToDelete", createdAt = 0L, updatedAt = 0L)
        viewModel.onAction(PlaylistListAction.OnDeletePlaylist(playlist))
        advanceUntilIdle()

        coVerify { playlistRepository.deletePlaylist(playlist) }
    }

    @Test
    fun `retry reloads playlists after error`() = runTest(testDispatcher) {
        val errorFlow = kotlinx.coroutines.flow.flow<List<com.rabbithole.musicbbit.domain.model.Playlist>> { throw RuntimeException("DB error") }
        every { playlistRepository.getAllPlaylists() } returns errorFlow

        val viewModel = PlaylistListViewModel(playlistRepository, createPlaylistUseCase)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PlaylistListUiState.Error)

        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PlaylistListUiState.Success)
    }
}
