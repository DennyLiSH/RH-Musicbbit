package com.rabbithole.musicbbit.data.repository

import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.MusicScanner
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.local.model.ScanDirectoryEntity
import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.data.local.sync.SongDiff
import com.rabbithole.musicbbit.data.local.sync.SongSyncEngine
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
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
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

    /** Domain Song — used as MusicScanner output and test expectations. */
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

    /** Entity Song — returned by mocked SongDao. */
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

    /** Entity ScanDirectory — returned by mocked ScanDirectoryDao. */
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
    fun `refreshSongs inserts new songs`() = runTest(testDispatcher) {
        val dir = scanDirEntity(path = "/storage/Music")
        val newSong = songEntity(id = 0L, path = "/storage/Music/new.mp3", title = "New Song")

        whenever(scanDirectoryDao.getAll()).thenReturn(flowOf(listOf(dir)))
        whenever(musicScanner.scanDirectories(listOf("/storage/Music"))).thenReturn(
            listOf(songDomain(id = 0L, path = "/storage/Music/new.mp3", title = "New Song"))
        )
        whenever(songDao.getAll()).thenReturn(flowOf(emptyList()))
        whenever(songSyncEngine.computeDiff(any(), any())).thenReturn(
            SongDiff(
                toInsert = listOf(newSong),
                toDelete = emptyList(),
                toUpdate = emptyList()
            )
        )
        wheneverBlocking { songDao.insertAll(any()) } doReturn emptyList()

        val result = repository.refreshSongs()

        assertTrue(result.isSuccess)
        verifyBlocking(songDao) {
            insertAll(argThat { size == 1 && this[0].path == "/storage/Music/new.mp3" })
        }
    }

    @Test
    fun `refreshSongs deletes removed songs`() = runTest(testDispatcher) {
        val dir = scanDirEntity(path = "/storage/Music")
        val existingSong = songEntity(id = 10L, path = "/storage/Music/old.mp3", title = "Old Song")

        whenever(scanDirectoryDao.getAll()).thenReturn(flowOf(listOf(dir)))
        whenever(musicScanner.scanDirectories(listOf("/storage/Music"))).thenReturn(emptyList())
        whenever(songDao.getAll()).thenReturn(flowOf(listOf(existingSong)))
        whenever(songSyncEngine.computeDiff(any(), any())).thenReturn(
            SongDiff(
                toInsert = emptyList(),
                toDelete = listOf(existingSong),
                toUpdate = emptyList()
            )
        )
        wheneverBlocking { songDao.delete(any()) } doReturn Unit

        val result = repository.refreshSongs()

        assertTrue(result.isSuccess)
        verifyBlocking(songDao) {
            delete(argThat { id == 10L && path == "/storage/Music/old.mp3" })
        }
    }

    @Test
    fun `refreshSongs returns failure on exception`() = runTest(testDispatcher) {
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
            // Second identical List is filtered by distinctUntilChanged(); flow goes straight to Complete
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
