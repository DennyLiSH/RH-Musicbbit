package com.rabbithole.musicbbit.data.repository

import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.local.model.PlaylistWithSongsEntity
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.Song
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: PlaylistRepositoryImpl

    @Before
    fun setup() {
        repository = PlaylistRepositoryImpl(playlistDao, playlistSongDao, testDispatcher)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun playlistEntity(
        id: Long = 1L,
        name: String = "Test Playlist",
        createdAt: Long = 1000L,
        updatedAt: Long = 2000L
    ) = Playlist(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt)

    private fun songEntity(
        id: Long,
        title: String = "Song $id",
        path: String = "/music/song$id.mp3",
        artist: String? = "Artist $id",
        album: String? = "Album $id",
        durationMs: Long = 180000L,
        dateAdded: Long = 3000L,
        coverUri: String? = null
    ) = Song(
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
        val song1 = songEntity(id = 10L, title = "Song A")
        val song2 = songEntity(id = 20L, title = "Song B")

        every { playlistDao.getAll() } returns flowOf(listOf(playlist))
        every { playlistSongDao.getByPlaylistId(1L) } returns flowOf(
            listOf(playlistSongEntity(playlistId = 1L, songId = 10L, sortOrder = 0),
                playlistSongEntity(playlistId = 1L, songId = 20L, sortOrder = 1))
        )
        coEvery { playlistDao.getPlaylistWithSongs(1L) } returns PlaylistWithSongsEntity(playlist, listOf(song1, song2))

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
        coEvery { playlistDao.getPlaylistWithSongs(2L) } returns PlaylistWithSongsEntity(playlist, emptyList())

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
    fun `playlist with partial songs - returns only available songs`() = runTest(testDispatcher) {
        val playlist = playlistEntity(id = 3L, name = "Partial Playlist")
        val existingSong = songEntity(id = 30L, title = "Still Here")

        every { playlistDao.getAll() } returns flowOf(listOf(playlist))
        every { playlistSongDao.getByPlaylistId(3L) } returns flowOf(
            listOf(playlistSongEntity(playlistId = 3L, songId = 30L, sortOrder = 0))
        )
        coEvery { playlistDao.getPlaylistWithSongs(3L) } returns PlaylistWithSongsEntity(playlist, listOf(existingSong))

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
        val playlistV1 = playlistEntity(id = 4L, name = "Old Name")
        val playlistV2 = playlistEntity(id = 4L, name = "New Name")

        val playlistFlow = MutableStateFlow(listOf(playlistV1))

        every { playlistDao.getAll() } returns playlistFlow
        every { playlistSongDao.getByPlaylistId(4L) } returns flowOf(emptyList())
        coEvery { playlistDao.getPlaylistWithSongs(4L) } returnsMany listOf(
            PlaylistWithSongsEntity(playlistV1, emptyList()),
            PlaylistWithSongsEntity(playlistV2, emptyList())
        )

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

    // ------------------------------------------------------------------
    // Scenario 6: addSongsToPlaylist - normal batch insert
    // ------------------------------------------------------------------

    @Test
    fun `addSongsToPlaylist - inserts all new songs with correct sortOrder`() = runTest(testDispatcher) {
        every { playlistSongDao.getByPlaylistId(1L) } returns flowOf(emptyList())
        coEvery { playlistSongDao.insertAll(any()) } returns Unit

        val result = repository.addSongsToPlaylist(1L, listOf(10L, 20L, 30L))

        assertTrue(result.isSuccess)
        coVerify {
            playlistSongDao.insertAll(match { entities: List<PlaylistSongEntity> ->
                entities.size == 3 &&
                entities[0].playlistId == 1L && entities[0].songId == 10L && entities[0].sortOrder == 0 &&
                entities[1].playlistId == 1L && entities[1].songId == 20L && entities[1].sortOrder == 1 &&
                entities[2].playlistId == 1L && entities[2].songId == 30L && entities[2].sortOrder == 2
            })
        }
    }

    // ------------------------------------------------------------------
    // Scenario 7: addSongsToPlaylist - filters out existing songs
    // ------------------------------------------------------------------

    @Test
    fun `addSongsToPlaylist - filters existing songs and inserts only new ones`() = runTest(testDispatcher) {
        val existingSongs = listOf(
            playlistSongEntity(playlistId = 1L, songId = 10L, sortOrder = 0)
        )
        every { playlistSongDao.getByPlaylistId(1L) } returns flowOf(existingSongs)
        coEvery { playlistSongDao.insertAll(any()) } returns Unit

        val result = repository.addSongsToPlaylist(1L, listOf(10L, 20L, 30L))

        assertTrue(result.isSuccess)
        coVerify {
            playlistSongDao.insertAll(match { entities: List<PlaylistSongEntity> ->
                entities.size == 2 &&
                entities[0].songId == 20L && entities[0].sortOrder == 1 &&
                entities[1].songId == 30L && entities[1].sortOrder == 2
            })
        }
    }

    // ------------------------------------------------------------------
    // Scenario 8: addSongsToPlaylist - empty list does nothing
    // ------------------------------------------------------------------

    @Test
    fun `addSongsToPlaylist - empty list does not call insertAll`() = runTest(testDispatcher) {
        val result = repository.addSongsToPlaylist(1L, emptyList())

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { playlistSongDao.insertAll(any()) }
    }

    // ------------------------------------------------------------------
    // Scenario 9: junction table change triggers flow re-emission (bug fix)
    // ------------------------------------------------------------------

    @Test
    fun `adding song to playlist_songs triggers flow re-emission`() = runTest(testDispatcher) {
        val playlist = playlistEntity(id = 5L, name = "Reactive Playlist")
        val song1 = songEntity(id = 50L, title = "First Song")
        val song2 = songEntity(id = 51L, title = "Second Song")

        val playlistFlow = MutableStateFlow(listOf(playlist))
        val junctionFlow = MutableStateFlow(emptyList<PlaylistSongEntity>())

        every { playlistDao.getAll() } returns playlistFlow
        every { playlistSongDao.getByPlaylistId(5L) } returns junctionFlow
        coEvery { playlistDao.getPlaylistWithSongs(5L) } returnsMany listOf(
            PlaylistWithSongsEntity(playlist, emptyList()),
            PlaylistWithSongsEntity(playlist, listOf(song1)),
            PlaylistWithSongsEntity(playlist, listOf(song1, song2))
        )

        repository.getPlaylistWithSongs(5L).test {
            // Initial emission: no songs in junction table
            val first = awaitItem()
            assertNotNull(first)
            assertEquals(0, first!!.songs.size)

            // Simulate adding a song to playlist_songs (junction table change)
            junctionFlow.emit(listOf(playlistSongEntity(playlistId = 5L, songId = 50L, sortOrder = 0)))

            val second = awaitItem()
            assertNotNull(second)
            assertEquals(1, second!!.songs.size)
            assertEquals("First Song", second.songs[0].title)

            // Simulate adding another song
            junctionFlow.emit(listOf(
                playlistSongEntity(playlistId = 5L, songId = 50L, sortOrder = 0),
                playlistSongEntity(playlistId = 5L, songId = 51L, sortOrder = 1)
            ))

            val third = awaitItem()
            assertNotNull(third)
            assertEquals(2, third!!.songs.size)
            assertEquals("First Song", third.songs[0].title)
            assertEquals("Second Song", third.songs[1].title)
        }
    }

    // ------------------------------------------------------------------
    // Scenario 10: songs sorted by sortOrder from junction table
    // ------------------------------------------------------------------

    @Test
    fun `songs are sorted by sortOrder from junction table`() = runTest(testDispatcher) {
        val playlist = playlistEntity(id = 6L, name = "Sorted Playlist")
        // Songs returned by Room in arbitrary order
        val songC = songEntity(id = 60L, title = "Song C")
        val songA = songEntity(id = 61L, title = "Song A")
        val songB = songEntity(id = 62L, title = "Song B")

        every { playlistDao.getAll() } returns flowOf(listOf(playlist))
        // Junction table defines order: songA=0, songB=1, songC=2
        every { playlistSongDao.getByPlaylistId(6L) } returns flowOf(
            listOf(
                playlistSongEntity(playlistId = 6L, songId = 61L, sortOrder = 0),
                playlistSongEntity(playlistId = 6L, songId = 62L, sortOrder = 1),
                playlistSongEntity(playlistId = 6L, songId = 60L, sortOrder = 2)
            )
        )
        // Room returns songs in different order than desired sort
        coEvery { playlistDao.getPlaylistWithSongs(6L) } returns PlaylistWithSongsEntity(
            playlist, listOf(songC, songA, songB)
        )

        repository.getPlaylistWithSongs(6L).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(3, result!!.songs.size)
            // Should be sorted by sortOrder: A(0), B(1), C(2)
            assertEquals("Song A", result.songs[0].title)
            assertEquals("Song B", result.songs[1].title)
            assertEquals("Song C", result.songs[2].title)
            awaitComplete()
        }
    }
}
