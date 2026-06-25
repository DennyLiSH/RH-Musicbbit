package com.rabbithole.musicbbit.data.repository

import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.local.model.ScanDirectoryEntity
import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.domain.model.ScanDirectory
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class ScanDirectoryRepositoryImplTest {

    private val scanDirectoryDao: ScanDirectoryDao = mock()
    private val songDao: SongDao = mock()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ScanDirectoryRepositoryImpl

    @Before
    fun setup() {
        repository = ScanDirectoryRepositoryImpl(scanDirectoryDao, songDao, testDispatcher)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun scanDirEntity(
        id: Long = 1L,
        path: String = "/storage/Music",
        name: String = "Music",
        addedAt: Long = 1000L
    ) = ScanDirectoryEntity(id = id, path = path, name = name, addedAt = addedAt)

    private fun songEntity(
        id: Long = 1L,
        path: String = "/storage/Music/song.mp3",
        title: String = "Song",
        artist: String? = null,
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
    fun `getAll maps entities to domain`() = runTest(testDispatcher) {
        val entities = listOf(
            scanDirEntity(id = 1L, path = "/storage/Music", name = "Music"),
            scanDirEntity(id = 2L, path = "/storage/Downloads", name = "Downloads")
        )
        whenever(scanDirectoryDao.getAll()).thenReturn(flowOf(entities))

        repository.getAll().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("/storage/Music", result[0].path)
            assertEquals("Music", result[0].name)
            assertEquals("/storage/Downloads", result[1].path)
            awaitComplete()
        }
    }

    @Test
    fun `add inserts and returns id`() = runTest(testDispatcher) {
        val directory = ScanDirectory(id = 0L, path = "/storage/Music", name = "Music", addedAt = 1000L)
        wheneverBlocking { scanDirectoryDao.insert(any()) } doReturn 5L

        val result = repository.add(directory)

        assertTrue(result.isSuccess)
        assertEquals(5L, result.getOrNull())
        verifyBlocking(scanDirectoryDao) {
            insert(argThat { path == "/storage/Music" && name == "Music" })
        }
    }

    @Test
    fun `remove cascades songs under directory path`() = runTest(testDispatcher) {
        val dir = scanDirEntity(id = 1L, path = "/storage/Music")
        wheneverBlocking { scanDirectoryDao.getById(1L) } doReturn dir
        whenever(songDao.getAll()).thenReturn(
            flowOf(
                listOf(
                    songEntity(id = 10L, path = "/storage/Music/song1.mp3"),
                    songEntity(id = 20L, path = "/storage/Music/sub/song2.mp3"),
                    songEntity(id = 30L, path = "/storage/Other/song3.mp3")
                )
            )
        )
        wheneverBlocking { songDao.delete(any()) } doReturn Unit
        wheneverBlocking { scanDirectoryDao.delete(any()) } doReturn Unit

        val result = repository.remove(1L)

        assertTrue(result.isSuccess)
        verifyBlocking(songDao, org.mockito.Mockito.times(2)) { delete(any()) }
        verifyBlocking(songDao) { delete(argThat { id == 10L }) }
        verifyBlocking(songDao) { delete(argThat { id == 20L }) }
        verifyBlocking(songDao, org.mockito.Mockito.never()) { delete(argThat { id == 30L }) }
        verifyBlocking(scanDirectoryDao) { delete(dir) }
    }

    @Test
    fun `remove does nothing for non-existent directory`() = runTest(testDispatcher) {
        wheneverBlocking { scanDirectoryDao.getById(99L) } doReturn null

        val result = repository.remove(99L)

        assertTrue(result.isSuccess)
        verifyBlocking(songDao, org.mockito.Mockito.never()) { delete(any()) }
        verifyBlocking(scanDirectoryDao, org.mockito.Mockito.never()) { delete(any()) }
    }

    @Test
    fun `add returns failure on DAO exception`() = runTest(testDispatcher) {
        val directory = ScanDirectory(id = 0L, path = "/storage/Music", name = "Music", addedAt = 1000L)
        wheneverBlocking { scanDirectoryDao.insert(any()) } doThrow android.database.sqlite.SQLiteException("disk full")

        val result = repository.add(directory)

        assertTrue(result.isFailure)
    }
}
