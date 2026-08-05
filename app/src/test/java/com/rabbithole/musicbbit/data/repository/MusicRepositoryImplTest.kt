package com.rabbithole.musicbbit.data.repository

import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.MusicScanner
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.local.model.ScanDirectoryEntity
import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.data.local.sync.SongSyncEngine
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
    private val musicScanner: MusicScanner = mock()
    private val songSyncEngine: SongSyncEngine = mock()
    private val songSorter: SongSorter = SongSorter()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: MusicRepositoryImpl

    @Before
    fun setup() {
        repository = MusicRepositoryImpl(
            songDao, scanDirectoryDao, musicScanner, songSyncEngine, songSorter, testDispatcher
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun songDomain(
        id: Long = 1L,
        path: String = "/music/song.mp3",
        title: String = "Song",
        artist: String? = "Artist",
        album: String? = null,
        durationMs: Long = 180000L,
        dateAdded: Long = 3000L,
        coverUri: String? = null
    ) = Song(
        id = id, path = path, title = title, artist = artist,
        album = album, durationMs = durationMs, dateAdded = dateAdded, coverUri = coverUri
    )

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

    private fun scanDirEntity(
        id: Long = 1L,
        path: String = "/storage/Music"
    ) = ScanDirectoryEntity(id = id, path = path, name = "Music", addedAt = 1000L)

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `refreshSongs with no directories clears all songs`() = runTest(testDispatcher) {
        whenever(scanDirectoryDao.getAll()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { songDao.deleteAll() } doReturn Unit

        val result = repository.refreshSongs()

        assertTrue(result.isSuccess)
        verifyBlocking(songDao) { deleteAll() }
        verifyBlocking(songDao, org.mockito.Mockito.never()) { insertAll(any()) }
    }

    @Test
    fun `refreshSongs delegates diff application to songSyncEngine sync`() = runTest(testDispatcher) {
        val dir = scanDirEntity(path = "/storage/Music")
        val newSong = songDomain(id = 0L, path = "/storage/Music/new.mp3", title = "New Song")

        whenever(scanDirectoryDao.getAll()).thenReturn(flowOf(listOf(dir)))
        whenever(musicScanner.scanDirectories(listOf("/storage/Music"))).thenReturn(listOf(newSong))
        whenever(songDao.getAll()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { songSyncEngine.sync(any(), any()) } doReturn SyncResult(inserted = 1, deleted = 0, updated = 0)

        val result = repository.refreshSongs()

        assertTrue(result.isSuccess)
        verifyBlocking(songSyncEngine) { sync(any(), any()) }
    }

    @Test
    fun `refreshDirectory delegates diff application to songSyncEngine sync`() = runTest(testDispatcher) {
        val existingSong = songEntity(id = 10L, path = "/storage/Music/old.mp3", title = "Old Song")
        whenever(musicScanner.scanDirectories(listOf("/storage/Music"))).thenReturn(emptyList())
        whenever(songDao.getByPathPrefix("/storage/Music")).thenReturn(flowOf(listOf(existingSong)))
        wheneverBlocking { songSyncEngine.sync(any(), any()) } doReturn SyncResult(inserted = 0, deleted = 1, updated = 0)

        val result = repository.refreshDirectory("/storage/Music")

        assertTrue(result.isSuccess)
        verifyBlocking(songSyncEngine) { sync(any(), any()) }
    }

    @Test
    fun `refreshSongs returns failure when sync throws`() = runTest(testDispatcher) {
        val dir = scanDirEntity(path = "/storage/Music")
        whenever(scanDirectoryDao.getAll()).thenReturn(flowOf(listOf(dir)))
        whenever(musicScanner.scanDirectories(listOf("/storage/Music"))).thenReturn(emptyList())
        whenever(songDao.getAll()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { songSyncEngine.sync(any(), any()) } doThrow RuntimeException("DB error")

        val result = repository.refreshSongs()

        assertTrue(result.isFailure)
        assertEquals("DB error", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `refreshSongs returns failure on scanDirectory exception`() = runTest(testDispatcher) {
        whenever(scanDirectoryDao.getAll()).thenThrow(RuntimeException("DB error"))

        val result = repository.refreshSongs()

        assertTrue(result.isFailure)
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
