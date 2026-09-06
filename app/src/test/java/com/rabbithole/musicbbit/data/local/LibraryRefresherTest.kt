package com.rabbithole.musicbbit.data.local

import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.local.sync.SongSyncEngine
import com.rabbithole.musicbbit.data.local.sync.SyncResult
import com.rabbithole.musicbbit.domain.model.Song
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
}
