package com.rabbithole.musicbbit.data.repository

import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.model.PlaylistEntity
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.data.model.SongEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistRepositoryImplTest {

    private val playlistDao: PlaylistDao = mockk()
    private val playlistSongDao: PlaylistSongDao = mockk()
    private val songDao: SongDao = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: PlaylistRepositoryImpl

    @Before
    fun setup() {
        repository = PlaylistRepositoryImpl(playlistDao, playlistSongDao, songDao, testDispatcher)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun playlistEntity(
        id: Long = 1L,
        name: String = "Test Playlist",
        createdAt: Long = 1000L,
        updatedAt: Long = 2000L
    ) = PlaylistEntity(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt)

    private fun songEntity(
        id: Long,
        title: String = "Song $id",
        path: String = "/music/song$id.mp3",
        artist: String? = "Artist $id",
        album: String? = "Album $id",
        durationMs: Long = 180000L,
        dateAdded: Long = 3000L,
        coverUri: String? = null
    ) = SongEntity(
        id = id,
        path = path,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        dateAdded = dateAdded,
        coverUri = coverUri
    )

    private fun playlistSongEntity(
        playlistId: Long = 1L,
        songId: Long,
        sortOrder: Int = 0
    ) = PlaylistSongEntity(playlistId = playlistId, songId = songId, sortOrder = sortOrder)

    // ------------------------------------------------------------------
    // Scenario 1: playlist exists with songs
    // ------------------------------------------------------------------

    @Test
    fun `playlist exists with songs - returns PlaylistWithSongs with correct data`() = runTest(testDispatcher) {
        val playlist = playlistEntity(id = 1L, name = "Morning Vibes")
        val playlistSongs = listOf(
            playlistSongEntity(playlistId = 1L, songId = 10L, sortOrder = 0),
            playlistSongEntity(playlistId = 1L, songId = 20L, sortOrder = 1)
        )
        val song1 = songEntity(id = 10L, title = "Song A")
        val song2 = songEntity(id = 20L, title = "Song B")

        every { playlistDao.getAll() } returns flowOf(listOf(playlist))
        every { playlistSongDao.getByPlaylistId(1L) } returns flowOf(playlistSongs)
        every { songDao.getById(10L) } returns song1
        every { songDao.getById(20L) } returns song2

        repository.getPlaylistWithSongs(1L).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Morning Vibes", result!!.playlist.name)
            assertEquals(2, result.songs.size)
            assertEquals("Song A", result.songs[0].title)
            assertEquals("Song B", result.songs[1].title)
            awaitComplete()
        }
    }

    // ------------------------------------------------------------------
    // Scenario 2: playlist exists but has no songs
    // ------------------------------------------------------------------

    @Test
    fun `playlist exists with no songs - returns PlaylistWithSongs with empty songs list`() = runTest(testDispatcher) {
        val playlist = playlistEntity(id = 2L, name = "Empty Playlist")

        every { playlistDao.getAll() } returns flowOf(listOf(playlist))
        every { playlistSongDao.getByPlaylistId(2L) } returns flowOf(emptyList())

        repository.getPlaylistWithSongs(2L).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Empty Playlist", result!!.playlist.name)
            assertTrue(result.songs.isEmpty())
            awaitComplete()
        }
    }

    // ------------------------------------------------------------------
    // Scenario 3: playlist does not exist
    // ------------------------------------------------------------------

    @Test
    fun `playlist does not exist - emits null`() = runTest(testDispatcher) {
        every { playlistDao.getAll() } returns flowOf(emptyList())
        every { playlistSongDao.getByPlaylistId(99L) } returns flowOf(emptyList())

        repository.getPlaylistWithSongs(99L).test {
            val result = awaitItem()
            assertNull(result)
            awaitComplete()
        }
    }

    // ------------------------------------------------------------------
    // Scenario 4: song deleted but junction table still has reference
    // ------------------------------------------------------------------

    @Test
    fun `song deleted but junction table has residual - filters out null songs`() = runTest(testDispatcher) {
        val playlist = playlistEntity(id = 3L, name = "Partial Playlist")
        val playlistSongs = listOf(
            playlistSongEntity(playlistId = 3L, songId = 30L, sortOrder = 0),
            playlistSongEntity(playlistId = 3L, songId = 40L, sortOrder = 1)
        )
        val existingSong = songEntity(id = 30L, title = "Still Here")

        every { playlistDao.getAll() } returns flowOf(listOf(playlist))
        every { playlistSongDao.getByPlaylistId(3L) } returns flowOf(playlistSongs)
        every { songDao.getById(30L) } returns existingSong
        every { songDao.getById(40L) } returns null

        repository.getPlaylistWithSongs(3L).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(1, result!!.songs.size)
            assertEquals("Still Here", result.songs[0].title)
            awaitComplete()
        }
    }

    // ------------------------------------------------------------------
    // Scenario 5: playlist data changes and flow re-emits
    // ------------------------------------------------------------------

    @Test
    fun `playlist data changes - flow re-emits updated value`() = runTest(testDispatcher) {
        val playlistFlow = MutableSharedFlow<List<PlaylistEntity>>(replay = 1)
        val playlistV1 = playlistEntity(id = 4L, name = "Old Name")
        val playlistV2 = playlistEntity(id = 4L, name = "New Name")

        every { playlistDao.getAll() } returns playlistFlow
        every { playlistSongDao.getByPlaylistId(4L) } returns flowOf(emptyList())

        playlistFlow.emit(listOf(playlistV1))

        repository.getPlaylistWithSongs(4L).test {
            val first = awaitItem()
            assertNotNull(first)
            assertEquals("Old Name", first!!.playlist.name)

            playlistFlow.emit(listOf(playlistV2))

            val second = awaitItem()
            assertNotNull(second)
            assertEquals("New Name", second!!.playlist.name)
        }
    }
}
