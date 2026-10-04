package com.rabbithole.musicbbit.presentation.playlist

import androidx.lifecycle.SavedStateHandle
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.presentation.components.ListUiState
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import dagger.hilt.android.testing.HiltTestApplication
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class PlaylistDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var musicRepository: MusicRepository

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
        musicRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load playlist with songs emits Content state`() = runTest {
        val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)
        val songs = listOf(
            Song(id = 1L, path = "/a.mp3", title = "Song A", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null),
            Song(id = 2L, path = "/b.mp3", title = "Song B", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null)
        )
        val playlistWithSongs = PlaylistWithSongs(playlist, songs)

        whenever(playlistRepository.getPlaylistWithSongs(1L)).thenReturn(flowOf(playlistWithSongs))
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()

        val state = viewModel.uiState.value as ListUiState.Content
        assertEquals(playlist, state.data?.playlist)
        assertEquals(2, state.data?.songs?.size)
    }

    @Test
    fun `remove song calls repository removeSongFromPlaylist`() = runTest {
        val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)
        val songs = listOf(
            Song(id = 1L, path = "/a.mp3", title = "Song A", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null)
        )
        val playlistWithSongs = PlaylistWithSongs(playlist, songs)

        whenever(playlistRepository.getPlaylistWithSongs(1L)).thenReturn(flowOf(playlistWithSongs))
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { playlistRepository.removeSongFromPlaylist(1L, 1L) } doReturn Result.success(Unit)

        val viewModel = createViewModel()

        viewModel.onAction(PlaylistDetailAction.OnRemoveSong(1L))
        testDispatcher.scheduler.advanceUntilIdle()

        verifyBlocking(playlistRepository) { removeSongFromPlaylist(1L, 1L) }
    }

    @Test
    fun `reorder songs sets reorderPreview then clears on success`() = runTest {
        val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)
        val songs = listOf(
            Song(id = 1L, path = "/a.mp3", title = "Song A", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null),
            Song(id = 2L, path = "/b.mp3", title = "Song B", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null),
            Song(id = 3L, path = "/c.mp3", title = "Song C", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null)
        )
        val playlistWithSongs = PlaylistWithSongs(playlist, songs)

        whenever(playlistRepository.getPlaylistWithSongs(1L)).thenReturn(flowOf(playlistWithSongs))
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { playlistRepository.reorderPlaylistSongs(eq(1L), any()) } doReturn Result.success(Unit)

        val viewModel = createViewModel()

        viewModel.onAction(PlaylistDetailAction.OnReorderSongs(fromIndex = 0, toIndex = 2))
        testDispatcher.scheduler.advanceUntilIdle()

        // After success the preview clears (rollback contract).
        assertNull(viewModel.reorderPreview.value)

        verifyBlocking(playlistRepository) { reorderPlaylistSongs(1L, listOf(2L, 3L, 1L)) }
    }

    @Test
    fun `add songs calls playlistRepository addSongsToPlaylist`() = runTest {
        val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)
        val playlistWithSongs = PlaylistWithSongs(playlist, emptyList())

        whenever(playlistRepository.getPlaylistWithSongs(1L)).thenReturn(flowOf(playlistWithSongs))
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { playlistRepository.addSongsToPlaylist(1L, listOf(1L, 2L)) } doReturn Result.success(Unit)

        val viewModel = createViewModel()

        viewModel.onAction(PlaylistDetailAction.OnAddSongs(listOf(1L, 2L)))
        testDispatcher.scheduler.advanceUntilIdle()

        verifyBlocking(playlistRepository) { addSongsToPlaylist(1L, listOf(1L, 2L)) }
    }

    @Test
    fun `null playlist emits Content with null data (not found)`() = runTest {
        whenever(playlistRepository.getPlaylistWithSongs(1L)).thenReturn(flowOf(null))
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListUiState.Content
        assertNull(state.data)
    }

    @Test
    fun `retry reloads playlist after error`() = runTest {
        val errorFlow = kotlinx.coroutines.flow.flow<PlaylistWithSongs?> { throw RuntimeException("DB error") }
        whenever(playlistRepository.getPlaylistWithSongs(1L)).thenReturn(errorFlow)
        whenever(musicRepository.getAllSongs()).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ListUiState.Error)

        // Replace stub with success flow before retry.
        val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)
        whenever(playlistRepository.getPlaylistWithSongs(1L)).thenReturn(flowOf(PlaylistWithSongs(playlist, emptyList())))
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "after retry with success stub, uiState should be Content (was: ${viewModel.uiState.value})",
            viewModel.uiState.value is ListUiState.Content
        )
    }

    private fun createViewModel(): PlaylistDetailViewModel {
        return PlaylistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("playlistId" to 1L)),
            playlistRepository = playlistRepository,
            musicRepository = musicRepository,
        )
    }
}