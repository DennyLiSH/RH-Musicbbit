package com.rabbithole.musicbbit.presentation.playlist

import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var playlistRepository: PlaylistRepository

    companion object {
        @JvmStatic
        @BeforeClass
        fun plantTimber() {
            Timber.uprootAll()
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {}
            })
        }
    }

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
    fun `uiState becomes Success with playlists when repository emits data`() = runTest {
        val playlists = listOf(
            Playlist(id = 1L, name = "Favorites", createdAt = 0L, updatedAt = 0L),
            Playlist(id = 2L, name = "Workout", createdAt = 0L, updatedAt = 0L)
        )
        every { playlistRepository.getAllPlaylists() } returns flowOf(playlists)

        val viewModel = PlaylistListViewModel(playlistRepository)

        val state = viewModel.uiState.value as PlaylistListUiState.Success
        assertEquals(2, state.playlists.size)
        assertEquals("Favorites", state.playlists[0].name)
        assertEquals("Workout", state.playlists[1].name)
    }

    @Test
    fun `uiState becomes Success with empty list when repository emits empty`() = runTest {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())

        val viewModel = PlaylistListViewModel(playlistRepository)

        val state = viewModel.uiState.value as PlaylistListUiState.Success
        assertTrue(state.playlists.isEmpty())
    }

    @Test
    fun `create playlist success does not set error`() = runTest {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())
        coEvery { playlistRepository.createPlaylist("New") } coAnswers { Result.success(3L) }

        val viewModel = PlaylistListViewModel(playlistRepository)

        viewModel.onAction(PlaylistListAction.OnCreatePlaylist("New"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as PlaylistListUiState.Success
        assertNull(state.errorMessageResId)
    }

    @Test
    fun `create playlist failure sets error in Success state`() = runTest {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())
        coEvery { playlistRepository.createPlaylist("New") } returns Result.failure(RuntimeException("Failed"))

        val viewModel = PlaylistListViewModel(playlistRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(PlaylistListAction.OnCreatePlaylist("New"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as PlaylistListUiState.Success
        assertEquals(com.rabbithole.musicbbit.R.string.playlist_error_add_song_failed, state.errorMessageResId)
    }

    @Test
    fun `delete playlist calls repository deletePlaylist`() = runTest {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())
        coEvery { playlistRepository.deletePlaylist(any()) } returns Result.success(Unit)

        val viewModel = PlaylistListViewModel(playlistRepository)

        val playlist = Playlist(id = 1L, name = "ToDelete", createdAt = 0L, updatedAt = 0L)
        viewModel.onAction(PlaylistListAction.OnDeletePlaylist(playlist))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { playlistRepository.deletePlaylist(playlist) }
    }

    @Test
    fun `retry reloads playlists after error`() = runTest {
        val errorFlow = kotlinx.coroutines.flow.flow<List<Playlist>> { throw RuntimeException("DB error") }
        every { playlistRepository.getAllPlaylists() } returns errorFlow

        val viewModel = PlaylistListViewModel(playlistRepository)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PlaylistListUiState.Error)

        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PlaylistListUiState.Success)
    }
}
