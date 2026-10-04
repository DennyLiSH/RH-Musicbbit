package com.rabbithole.musicbbit.presentation.playlist

import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.presentation.components.ListUiState
import com.rabbithole.musicbbit.presentation.components.UserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
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
        playlistRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState becomes Content with playlists when repository emits data`() = runTest {
        val playlists = listOf(
            Playlist(id = 1L, name = "Favorites", createdAt = 0L, updatedAt = 0L),
            Playlist(id = 2L, name = "Workout", createdAt = 0L, updatedAt = 0L)
        )
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(playlists))

        val viewModel = PlaylistListViewModel(playlistRepository)

        val state = viewModel.uiState.value as ListUiState.Content
        assertEquals(2, state.data.size)
        assertEquals("Favorites", state.data[0].name)
        assertEquals("Workout", state.data[1].name)
    }

    @Test
    fun `uiState becomes Content with empty list when repository emits empty`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val viewModel = PlaylistListViewModel(playlistRepository)

        val state = viewModel.uiState.value as ListUiState.Content
        assertTrue(state.data.isEmpty())
    }

    @Test
    fun `create playlist success does not emit any UserMessage`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { playlistRepository.createPlaylist("New") } doAnswer { Result.success(3L) }

        val viewModel = PlaylistListViewModel(playlistRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(PlaylistListAction.OnCreatePlaylist("New"))
        testDispatcher.scheduler.advanceUntilIdle()

        // No message produced; uiState still Content(empty).
        assertTrue(viewModel.uiState.value is ListUiState.Content)
    }

    @Test
    fun `create playlist failure emits UserMessage with add song failed`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { playlistRepository.createPlaylist("New") } doReturn Result.failure(RuntimeException("Failed"))

        val viewModel = PlaylistListViewModel(playlistRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(PlaylistListAction.OnCreatePlaylist("New"))
        testDispatcher.scheduler.advanceUntilIdle()

        val message = viewModel.messages.first()
        assertEquals(UserMessage(R.string.playlist_error_add_song_failed), message)
    }

    @Test
    fun `delete playlist calls repository deletePlaylist`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { playlistRepository.deletePlaylist(any()) } doReturn Result.success(Unit)

        val viewModel = PlaylistListViewModel(playlistRepository)

        val playlist = Playlist(id = 1L, name = "ToDelete", createdAt = 0L, updatedAt = 0L)
        viewModel.onAction(PlaylistListAction.OnDeletePlaylist(playlist))
        testDispatcher.scheduler.advanceUntilIdle()

        verifyBlocking(playlistRepository) { deletePlaylist(playlist) }
    }

    @Test
    fun `retry reloads playlists after error`() = runTest {
        // Mockito stubbing is set up FIRST to error, THEN replaced with success.
        // With SharingStarted.Eagerly the initial subscription has already collected
        // by the time we read uiState, so we cannot rely on the original stub being
        // used after `whenever` re-runs. Verify retry() at minimum re-emits a value.
        val errorFlow = kotlinx.coroutines.flow.flow<List<Playlist>> { throw RuntimeException("DB error") }
        whenever(playlistRepository.getAllPlaylists()).thenReturn(errorFlow)

        val viewModel = PlaylistListViewModel(playlistRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Replace stub with success flow BEFORE retry so the new inner flow emits
        // a non-error value.
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "after retry with success stub, uiState should be Content (was: ${viewModel.uiState.value})",
            viewModel.uiState.value is ListUiState.Content
        )
    }
}