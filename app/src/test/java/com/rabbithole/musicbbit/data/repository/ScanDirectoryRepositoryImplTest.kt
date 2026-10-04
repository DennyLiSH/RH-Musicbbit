package com.rabbithole.musicbbit.data.repository

import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.LibraryRefresher
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.model.ScanDirectoryEntity
import com.rabbithole.musicbbit.data.local.sync.SyncResult
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * Verifies the repository delegates add/remove to LibraryRefresher — the actual
 * cascade / refresh semantics are exercised by LibraryRefresherTest, not here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanDirectoryRepositoryImplTest {

    private val scanDirectoryDao: ScanDirectoryDao = mock()
    private val libraryRefresher: LibraryRefresher = mock()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ScanDirectoryRepositoryImpl

    @Before
    fun setup() {
        repository = ScanDirectoryRepositoryImpl(scanDirectoryDao, libraryRefresher, testDispatcher)
    }

    private fun scanDirEntity(
        id: Long = 1L,
        path: String = "/storage/Music",
        name: String = "Music",
        addedAt: Long = 1000L
    ) = ScanDirectoryEntity(id = id, path = path, name = name, addedAt = addedAt)

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
    fun `add delegates to libraryRefresher addDirectoryAndRefresh`() = runTest(testDispatcher) {
        val directory = ScanDirectory(id = 0L, path = "/storage/Music", name = "Music", addedAt = 1000L)
        wheneverBlocking { libraryRefresher.addDirectoryAndRefresh(directory) } doReturn Result.success(5L)

        val result = repository.add(directory)

        assertTrue(result.isSuccess)
        assertEquals(5L, result.getOrNull())
        verifyBlocking(libraryRefresher) { addDirectoryAndRefresh(directory) }
    }

    @Test
    fun `remove delegates to libraryRefresher removeDirectoryAndCascade`() = runTest(testDispatcher) {
        wheneverBlocking { libraryRefresher.removeDirectoryAndCascade(1L) } doReturn Result.success(Unit)

        val result = repository.remove(1L)

        assertTrue(result.isSuccess)
        verifyBlocking(libraryRefresher) { removeDirectoryAndCascade(1L) }
    }

    @Test
    fun `add returns failure when refresh fails`() = runTest(testDispatcher) {
        val directory = ScanDirectory(id = 0L, path = "/storage/Music", name = "Music", addedAt = 1000L)
        wheneverBlocking { libraryRefresher.addDirectoryAndRefresh(directory) } doReturn
            Result.failure(android.database.sqlite.SQLiteException("scan failed"))

        val result = repository.add(directory)

        assertTrue(result.isFailure)
    }

    @Test
    fun `remove returns failure on exception`() = runTest(testDispatcher) {
        wheneverBlocking { libraryRefresher.removeDirectoryAndCascade(99L) } doThrow RuntimeException("db down")

        val result = repository.remove(99L)

        assertTrue(result.isFailure)
    }
}