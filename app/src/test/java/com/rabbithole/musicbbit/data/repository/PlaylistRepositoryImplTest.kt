package com.rabbithole.musicbbit.data.repository

import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.local.model.PlaylistEntity
import com.rabbithole.musicbbit.data.local.model.PlaylistWithSongsEntity
import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistRepositoryImplTest {

    private val playlistDao: PlaylistDao = mock()
    private val playlistSongDao: PlaylistSongDao = mock()
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
    // getPlaylistWithSongs tests
    // ------------------------------------------------------------------

    @Test
    fun `playlist exists with songs - returns PlaylistWithSongs with correct data`() = runTest(testDispatcher) {
        val playlist = playlistEntity(id = 1L, name = "Morning Vibes")
        val song1 = songEntity(id = 10L, title = "Song A")
        val song2 = songEntity(id = 20L, title = "Song B")

        whenever(playlistDao.observeWithSongs(1L)).thenReturn(
            flowOf(PlaylistWithSongsEntity(playlist, listOf(song1, song2)))
        )
        whenever(playlistSongDao.getByPlaylistId(1L)).thenReturn(
            flowOf(
                listOf(
                    playlistSongEntity(playlistId = 1L, songId = 10L, sortOrder = 0),
                    playlistSongEntity(playlistId = 1L, songId = 20L, sortOrder = 1)
                )
            )
        )

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

    @Test
    fun `playlist exists with no songs - returns PlaylistWithSongs with empty songs list`() = runTest(testDispatcher) {
        val playlist = playlistEntity(id = 2L, name = "Empty Playlist")

        whenever(playlistDao.observeWithSongs(2L)).thenReturn(
            flowOf(PlaylistWithSongsEntity(playlist, emptyList()))
        )
        whenever(playlistSongDao.getByPlaylistId(2L)).thenReturn(flowOf(emptyList()))

        repository.getPlaylistWithSongs(2L).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Empty Playlist", result!!.playlist.name)
            assertTrue(result.songs.isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun `playlist does not exist - emits null`() = runTest(testDispatcher) {
        whenever(playlistDao.observeWithSongs(99L)).thenReturn(flowOf(null))
        whenever(playlistSongDao.getByPlaylistId(99L)).thenReturn(flowOf(emptyList()))

        repository.getPlaylistWithSongs(99L).test {
            val result = awaitItem()
            assertNull(result)
            awaitComplete()
        }
    }

    @Test
    fun `playlist with partial songs - returns only available songs`() = runTest(testDispatcher) {
        val playlist = playlistEntity(id = 3L, name = "Partial Playlist")
        val existingSong = songEntity(id = 30L, title = "Still Here")

        whenever(playlistDao.observeWithSongs(3L)).thenReturn(
            flowOf(PlaylistWithSongsEntity(playlist, listOf(existingSong)))
        )
        whenever(playlistSongDao.getByPlaylistId(3L)).thenReturn(
            flowOf(listOf(playlistSongEntity(playlistId = 3L, songId = 30L, sortOrder = 0)))
        )

        repository.getPlaylistWithSongs(3L).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(1, result!!.songs.size)
            assertEquals("Still Here", result.songs[0].title)
            awaitComplete()
        }
    }

    @Test
    fun `playlist data changes - flow re-emits updated value`() = runTest(testDispatcher) {
        val playlistV1 = playlistEntity(id = 4L, name = "Old Name")
        val playlistV2 = playlistEntity(id = 4L, name = "New Name")

        val playlistFlow = MutableStateFlow(PlaylistWithSongsEntity(playlistV1, emptyList()))

        whenever(playlistDao.observeWithSongs(4L)).thenReturn(playlistFlow)
        whenever(playlistSongDao.getByPlaylistId(4L)).thenReturn(flowOf(emptyList()))

        repository.getPlaylistWithSongs(4L).test {
            val first = awaitItem()
            assertNotNull(first)
            assertEquals("Old Name", first!!.playlist.name)

            playlistFlow.emit(PlaylistWithSongsEntity(playlistV2, emptyList()))

            val second = awaitItem()
            assertNotNull(second)
            assertEquals("New Name", second!!.playlist.name)
        }
    }

    // ------------------------------------------------------------------
    // Other operations tests (unchanged)
    // ------------------------------------------------------------------

    @Test
    fun `createPlaylist - inserts and returns id`() = runTest(testDispatcher) {
        org.mockito.kotlin.wheneverBlocking { playlistDao.insert(any()) } doReturn 5L

        val result = repository.createPlaylist("New Playlist")

        assertTrue(result.isSuccess)
        assertEquals(5L, result.getOrNull())
        verifyBlocking(playlistDao) { insert(any()) }
    }

    @Test
    fun `createPlaylist - returns failure on DAO exception`() = runTest(testDispatcher) {
        org.mockito.kotlin.wheneverBlocking { playlistDao.insert(any()) } doAnswer {
            throw android.database.sqlite.SQLiteException("DB error")
        }

        val result = repository.createPlaylist("Bad")

        assertTrue(result.isFailure)
    }

    @Test
    fun `getPlaylistById returns mapped domain when entity exists`() = runTest(testDispatcher) {
        val entity = playlistEntity(id = 1L, name = "Found")
        whenever(playlistDao.getById(1L)).thenReturn(entity)

        val result = repository.getPlaylistById(1L)

        assertNotNull(result)
        assertEquals("Found", result?.name)
    }

    @Test
    fun `getPlaylistById returns null when entity missing`() = runTest(testDispatcher) {
        whenever(playlistDao.getById(99L)).thenReturn(null)

        val result = repository.getPlaylistById(99L)

        assertNull(result)
    }

    @Test
    fun `deletePlaylist calls dao`() = runTest(testDispatcher) {
        repository.deletePlaylist(
            com.rabbithole.musicbbit.domain.model.Playlist(
                id = 1L, name = "X", createdAt = 0L, updatedAt = 0L,
            )
        )

        verifyBlocking(playlistDao) { delete(any()) }
    }

    @Test
    fun `addSongsToPlaylist - inserts all new songs with correct sortOrder`() = runTest(testDispatcher) {
        whenever(playlistSongDao.getByPlaylistId(1L)).thenReturn(flowOf(emptyList()))

        val result = repository.addSongsToPlaylist(playlistId = 1L, songIds = listOf(10L, 20L, 30L))

        assertTrue(result.isSuccess)
        verifyBlocking(playlistSongDao) { insertAll(argThat { list -> list.size == 3 && list[0].sortOrder == 0 }) }
    }

    @Test
    fun `addSongsToPlaylist - filters existing songs and inserts only new ones`() = runTest(testDispatcher) {
        whenever(playlistSongDao.getByPlaylistId(1L)).thenReturn(
            flowOf(listOf(playlistSongEntity(playlistId = 1L, songId = 10L, sortOrder = 0)))
        )

        val result = repository.addSongsToPlaylist(playlistId = 1L, songIds = listOf(10L, 20L))

        assertTrue(result.isSuccess)
        verifyBlocking(playlistSongDao) { insertAll(argThat { list -> list.size == 1 && list[0].songId == 20L && list[0].sortOrder == 1 }) }
    }

    @Test
    fun `addSongsToPlaylist - empty list does not call insertAll`() = runTest(testDispatcher) {
        val result = repository.addSongsToPlaylist(playlistId = 1L, songIds = emptyList())

        assertTrue(result.isSuccess)
        org.mockito.kotlin.verify(playlistSongDao, org.mockito.kotlin.never()).insertAll(any())
    }

    // Note: playlist_songs invalidation is verified by PlaylistSongEntityForeignKeyTest
// (androidTest) + Migration10To11Test (JVM, FK CASCADE). The unit-level re-emission
// guarantee is exercised by Room itself via the @Transaction annotation on
// observeWithSongs — no separate JVM test (would require an in-memory Room database).
}