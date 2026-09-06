package com.rabbithole.musicbbit.data.repository

import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.LibraryRefresher
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.local.model.ScanDirectoryEntity
import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.data.local.sync.SyncResult
import com.rabbithole.musicbbit.domain.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class MusicRepositoryImplTest {

    private val songDao: SongDao = mock()
    private val scanDirectoryDao: ScanDirectoryDao = mock()
    private val libraryRefresher: LibraryRefresher = mock()
    private val songSorter: SongSorter = SongSorter()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: MusicRepositoryImpl

    @Before
    fun setup() {
        repository = MusicRepositoryImpl(
            songDao, scanDirectoryDao, libraryRefresher, songSorter, testDispatcher
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun songEntity(
        id: Long = 1L,
        path: String = "/music/song.mp3",
        title: String = "Song",
        artist: String? = "Artist",
        album: String? = null,
        durationMs: Long = 180000L,
        dateAdded: Long = 3000L,
        coverUri: String? = null
    ) = SongEntity(
        id = id, path = path, title = title, artist = artist,
        album = album, durationMs = durationMs, dateAdded = dateAdded, coverUri = coverUri
    )

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `refreshSongs delegates to LibraryRefresher`() = runTest(testDispatcher) {
        wheneverBlocking { libraryRefresher.refreshAll() } doReturn
            Result.success(SyncResult(inserted = 1, deleted = 0, updated = 0))

        val result = repository.refreshSongs()

        assertTrue(result.isSuccess)
        verifyBlocking(libraryRefresher) { refreshAll() }
    }

    @Test
    fun `refreshDirectory delegates to LibraryRefresher`() = runTest(testDispatcher) {
        wheneverBlocking { libraryRefresher.refreshDirectory("/storage/Music") } doReturn
            Result.success(SyncResult(inserted = 0, deleted = 1, updated = 0))

        val result = repository.refreshDirectory("/storage/Music")

        assertTrue(result.isSuccess)
        verifyBlocking(libraryRefresher) { refreshDirectory("/storage/Music") }
    }

    @Test
    fun `refreshSongs returns failure when the refresher fails`() = runTest(testDispatcher) {
        wheneverBlocking { libraryRefresher.refreshAll() } doReturn
            Result.failure(RuntimeException("DB error"))

        val result = repository.refreshSongs()

        assertTrue(result.isFailure)
        assertEquals("DB error", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `searchSongs delegates to songDao and sorts result`() = runTest(testDispatcher) {
        val songs = listOf(
            songEntity(id = 1L, title = "Hello World", artist = "Artist A"),
            songEntity(id = 2L, title = "Hello Again", artist = "Artist B")
        )
        whenever(songDao.searchSongs("hello")).thenReturn(flowOf(songs))

        repository.searchSongs("hello").test {
            val results = awaitItem()
            assertEquals(2, results.size)
            assertEquals("Hello Again", results[0].title)
            assertEquals("Hello World", results[1].title)
            awaitComplete()
        }
    }

    @Test
    fun `getAllSongs does not re-emit when dao emits identical entity list`() = runTest(testDispatcher) {
        val entities = listOf(
            songEntity(id = 1L, title = "Apple"),
            songEntity(id = 2L, title = "Zebra")
        )
        whenever(songDao.getAll()).thenReturn(flowOf(entities, entities))

        repository.getAllSongs().test {
            val first = awaitItem()
            assertEquals(2, first.size)
            awaitComplete()
        }
    }

    @Test
    fun `searchSongs does not re-emit when dao emits identical entity list`() = runTest(testDispatcher) {
        val entities = listOf(
            songEntity(id = 1L, title = "Apple"),
            songEntity(id = 2L, title = "Zebra")
        )
        whenever(songDao.searchSongs(any())).thenReturn(flowOf(entities, entities))

        repository.searchSongs("query").test {
            val first = awaitItem()
            assertEquals(2, first.size)
            awaitComplete()
        }
    }
}
