package com.rabbithole.musicbbit.presentation.playlist

import androidx.lifecycle.SavedStateHandle
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.domain.usecase.AddSongToPlaylistUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var musicRepository: MusicRepository
    private lateinit var addSongToPlaylistUseCase: AddSongToPlaylistUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playlistRepository = mockk(relaxed = true)
        musicRepository = mockk(relaxed = true)
        addSongToPlaylistUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load playlist with songs emits Success state`() = runTest {
        val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)
        val songs = listOf(
            Song(id = 1L, path = "/a.mp3", title = "Song A", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null),
            Song(id = 2L, path = "/b.mp3", title = "Song B", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null)
        )
        val playlistWithSongs = PlaylistWithSongs(playlist, songs)

        every { playlistRepository.getPlaylistWithSongs(1L) } returns flowOf(playlistWithSongs)
        every { musicRepository.getAllSongs() } returns flowOf(emptyList())

        val viewModel = createViewModel()

        val state = viewModel.uiState.value as PlaylistDetailUiState.Success
        assertEquals(playlist, state.playlistWithSongs.playlist)
        assertEquals(2, state.playlistWithSongs.songs.size)
    }

    @Test
    fun `remove song calls repository removeSongFromPlaylist`() = runTest {
        val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)
        val songs = listOf(
            Song(id = 1L, path = "/a.mp3", title = "Song A", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null)
        )
        val playlistWithSongs = PlaylistWithSongs(playlist, songs)

        every { playlistRepository.getPlaylistWithSongs(1L) } returns flowOf(playlistWithSongs)
        every { musicRepository.getAllSongs() } returns flowOf(emptyList())
        coEvery { playlistRepository.removeSongFromPlaylist(1L, 1L) } returns Result.success(Unit)

        val viewModel = createViewModel()

        viewModel.onAction(PlaylistDetailAction.OnRemoveSong(1L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { playlistRepository.removeSongFromPlaylist(1L, 1L) }
    }

    @Test
    fun `reorder songs updates ui state and calls reorderPlaylistSongs`() = runTest {
        val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)
        val songs = listOf(
            Song(id = 1L, path = "/a.mp3", title = "Song A", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null),
            Song(id = 2L, path = "/b.mp3", title = "Song B", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null),
            Song(id = 3L, path = "/c.mp3", title = "Song C", artist = null, album = null, durationMs = 1000L, dateAdded = 0L, coverUri = null)
        )
        val playlistWithSongs = PlaylistWithSongs(playlist, songs)

        every { playlistRepository.getPlaylistWithSongs(1L) } returns flowOf(playlistWithSongs)
        every { musicRepository.getAllSongs() } returns flowOf(emptyList())
        coEvery { playlistRepository.reorderPlaylistSongs(1L, any()) } returns Result.success(Unit)

        val viewModel = createViewModel()

        viewModel.onAction(PlaylistDetailAction.OnReorderSongs(fromIndex = 0, toIndex = 2))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as PlaylistDetailUiState.Success
        assertEquals(3, state.playlistWithSongs.songs.size)
        assertEquals("Song B", state.playlistWithSongs.songs[0].title)
        assertEquals("Song C", state.playlistWithSongs.songs[1].title)
        assertEquals("Song A", state.playlistWithSongs.songs[2].title)

        coVerify { playlistRepository.reorderPlaylistSongs(1L, listOf(2L, 3L, 1L)) }
    }

    @Test
    fun `add songs calls addSongToPlaylistUseCase`() = runTest {
        val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)
        val playlistWithSongs = PlaylistWithSongs(playlist, emptyList())

        every { playlistRepository.getPlaylistWithSongs(1L) } returns flowOf(playlistWithSongs)
        every { musicRepository.getAllSongs() } returns flowOf(emptyList())
        coEvery { addSongToPlaylistUseCase(1L, listOf(1L, 2L)) } returns Result.success(Unit)

        val viewModel = createViewModel()

        viewModel.onAction(PlaylistDetailAction.OnAddSongs(listOf(1L, 2L)))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { addSongToPlaylistUseCase(1L, listOf(1L, 2L)) }
    }

    @Test
    fun `retry reloads playlist after error`() = runTest {
        val errorFlow = kotlinx.coroutines.flow.flow<PlaylistWithSongs?> { throw RuntimeException("DB error") }
        every { playlistRepository.getPlaylistWithSongs(1L) } returns errorFlow
        every { musicRepository.getAllSongs() } returns flowOf(emptyList())

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value is PlaylistDetailUiState.Error)

        val playlist = Playlist(id = 1L, name = "Test", createdAt = 0L, updatedAt = 0L)
        every { playlistRepository.getPlaylistWithSongs(1L) } returns flowOf(PlaylistWithSongs(playlist, emptyList()))
        viewModel.retry()

        assertTrue(viewModel.uiState.value is PlaylistDetailUiState.Success)
    }

    private fun createViewModel(): PlaylistDetailViewModel {
        return PlaylistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("playlistId" to 1L)),
            playlistRepository = playlistRepository,
            musicRepository = musicRepository,
            addSongToPlaylistUseCase = addSongToPlaylistUseCase
        )
    }
}
