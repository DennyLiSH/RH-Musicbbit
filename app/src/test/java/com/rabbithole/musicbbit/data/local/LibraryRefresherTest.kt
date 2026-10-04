package com.rabbithole.musicbbit.data.local

import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.local.model.ScanDirectoryEntity
import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.data.local.sync.SongSyncEngine
import com.rabbithole.musicbbit.data.local.sync.SyncResult
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.model.Song
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * JVM tests for the refresh recipe and its explicit policies (empty-directory clears the
 * library; per-directory refresh never does).
 */
class LibraryRefresherTest {

    private val musicScanner: MusicScanner = mock()
    private val songDao: SongDao = mock()
    private val scanDirectoryDao: ScanDirectoryDao = mock()
    private val songSyncEngine: SongSyncEngine = mock()

    private val refresher = LibraryRefresher(
        musicScanner = musicScanner,
        songDao = songDao,
        scanDirectoryDao = scanDirectoryDao,
        songSyncEngine = songSyncEngine,
        ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
    )

    private fun song(path: String) = Song(
        id = 0L, path = path, title = path, artist = null, album = null,
        durationMs = 0L, dateAdded = 0L, coverUri = null,
    )

    @Test
    fun `refreshAll with no scan directories clears the library`() = runTest {
        whenever(scanDirectoryDao.getAll()).thenReturn(flowOf(emptyList()))

        val result = refresher.refreshAll()

        assertTrue(result.isSuccess)
        songDao.let { verify(it).deleteAll() }
        verify(musicScanner, never()).scanDirectories(any())
    }

    @Test
    fun `refreshAll scans directories and applies the sync diff`() = runTest {
        whenever(scanDirectoryDao.getAll()).thenReturn(flowOf(listOf(mock())))
        val scanned = listOf(song("/music/a.mp3"))
        whenever(musicScanner.scanDirectories(any())).thenReturn(scanned)
        whenever(songDao.getAll()).thenReturn(flowOf(emptyList()))
        whenever(songSyncEngine.sync(any(), any())).thenReturn(
            SyncResult(inserted = 0, deleted = 0, updated = 0)
        )

        val result = refresher.refreshAll()

        assertTrue(result.isSuccess)
        verify(songSyncEngine).sync(scanned, emptyList())
    }

    @Test
    fun `refreshDirectory syncs only that path prefix`() = runTest {
        whenever(musicScanner.scanDirectories(listOf("/music/dir1"))).thenReturn(emptyList())
        whenever(songDao.getByPathPrefix("/music/dir1")).thenReturn(flowOf(emptyList()))
        whenever(songSyncEngine.sync(any(), any())).thenReturn(
            SyncResult(inserted = 0, deleted = 0, updated = 0)
        )

        val result = refresher.refreshDirectory("/music/dir1")

        assertTrue(result.isSuccess)
        verify(songDao, never()).deleteAll()
        verify(songDao).getByPathPrefix(eq("/music/dir1"))
    }

    @Test
    fun `scanner failures surface as Result failure`() = runTest {
        whenever(scanDirectoryDao.getAll()).thenReturn(flowOf(listOf(mock())))
        whenever(musicScanner.scanDirectories(any())).thenThrow(IllegalStateException("boom"))

        val result = refresher.refreshAll()

        assertTrue(result.isFailure)
    }

    private fun domainScanDirectory(id: Long = 0L, path: String = "/music") = ScanDirectory(
        id = id, path = path, name = "name", addedAt = 0L
    )

    private fun scanEntity(id: Long = 0L, path: String = "/music") = ScanDirectoryEntity(
        id = id, path = path, name = "name", addedAt = 0L
    )

    private fun songEntity(id: Long = 0L, path: String = "/a.mp3") = SongEntity(
        id = id, path = path, title = "t", artist = null, album = null,
        durationMs = 1000L, dateAdded = 0L, coverUri = null,
    )

    @Test
    fun `addDirectoryAndRefresh inserts directory then refreshes library`() = runTest {
        // refreshAll path needs a non-empty directory list (else it calls deleteAll).
        whenever(scanDirectoryDao.getAll()).thenReturn(flowOf(listOf(scanEntity(path = "/music"))))
        wheneverBlocking { scanDirectoryDao.insert(any()) } doReturn 7L
        whenever(songDao.getAll()).thenReturn(flowOf(emptyList()))
        whenever(musicScanner.scanDirectories(any())).thenReturn(emptyList())
        whenever(songSyncEngine.sync(any(), any())).thenReturn(
            SyncResult(inserted = 0, deleted = 0, updated = 0)
        )

        val result = refresher.addDirectoryAndRefresh(domainScanDirectory(path = "/music"))

        assertTrue(result.isSuccess)
        assertEquals(7L, result.getOrNull())
        verify(scanDirectoryDao).insert(any())
        verify(musicScanner).scanDirectories(any())
    }

    @Test
    fun `removeDirectoryAndCascade deletes songs and directory in one transaction`() = runTest {
        wheneverBlocking { scanDirectoryDao.getById(3L) } doReturn scanEntity(id = 3L, path = "/a")
        val songs = listOf(
            songEntity(id = 1L, path = "/a/x.mp3"),
            songEntity(id = 2L, path = "/a/y.mp3"),
        )
        whenever(songDao.getAll()).thenReturn(flowOf(songs))

        val result = refresher.removeDirectoryAndCascade(3L)

        assertTrue(result.isSuccess)
        verify(songDao).deleteAllSongs(songs)
        verify(scanDirectoryDao).delete(scanEntity(id = 3L, path = "/a"))
    }

    @Test
    fun `removeDirectoryAndCascade is a no-op success for unknown id`() = runTest {
        wheneverBlocking { scanDirectoryDao.getById(99L) } doReturn null

        val result = refresher.removeDirectoryAndCascade(99L)

        assertTrue(result.isSuccess)
        verify(songDao, never()).deleteAllSongs(any())
    }

    @Test
    fun `removeDirectoryAndCascade respects directory boundaries`() = runTest {
        // Regression for the sibling-prefix bug: removing "/storage/Music" must keep
        // "/storage/MusicBox/x.mp3" (an old in-memory startsWith filter would over-delete).
        val entity = scanEntity(id = 3L, path = "/storage/Music")
        wheneverBlocking { scanDirectoryDao.getById(3L) } doReturn entity
        val songs = listOf(
            songEntity(id = 1L, path = "/storage/Music/a.mp3"),
            songEntity(id = 2L, path = "/storage/MusicBox/b.mp3"),
        )
        whenever(songDao.getAll()).thenReturn(flowOf(songs))

        refresher.removeDirectoryAndCascade(3L)

        verify(songDao).deleteAllSongs(listOf(songs[0]))
    }

    @Test
    fun `addDirectoryAndRefresh reports failure but keeps the directory row when refresh fails`() = runTest {
        wheneverBlocking { scanDirectoryDao.insert(any()) } doReturn 5L
        // refreshAll fails — the insert is already persisted.
        whenever(songDao.getAll()).thenReturn(flowOf(emptyList()))
        whenever(musicScanner.scanDirectories(any())) doThrow RuntimeException("scan failed")

        val result = refresher.addDirectoryAndRefresh(domainScanDirectory(path = "/music"))

        assertTrue(result.isFailure)
        verify(scanDirectoryDao).insert(any())  // row persisted; recovery = retry refreshAll
    }
}
