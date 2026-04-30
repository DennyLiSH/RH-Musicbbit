package com.rabbithole.musicbbit.data.repository

import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.MusicScanner
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.model.Song
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicRepositoryImplTest {

    private val songDao: SongDao = mockk()
    private val scanDirectoryDao: ScanDirectoryDao = mockk()
    private val musicScanner: MusicScanner = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: MusicRepositoryImpl

    @Before
    fun setup() {
        repository = MusicRepositoryImpl(songDao, scanDirectoryDao, musicScanner, testDispatcher)
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
    ) = Song(
        id = id, path = path, title = title, artist = artist,
        album = album, durationMs = durationMs, dateAdded = dateAdded, coverUri = coverUri
    )

    private fun scanDirectoryEntity(
        id: Long = 1L,
        path: String = "/storage/Music"
    ) = ScanDirectory(id = id, path = path, name = "Music", addedAt = 1000L)

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `refreshSongs with no directories clears all songs`() = runTest(testDispatcher) {
        every { scanDirectoryDao.getAll() } returns flowOf(emptyList())
        coEvery { songDao.deleteAll() } returns Unit

        val result = repository.refreshSongs()

        assertTrue(result.isSuccess)
        coVerify { songDao.deleteAll() }
        coVerify(exactly = 0) { songDao.insertAll(any()) }
    }

    @Test
    fun `refreshSongs inserts new songs`() = runTest(testDispatcher) {
        val dir = scanDirectoryEntity(path = "/storage/Music")
        every { scanDirectoryDao.getAll() } returns flowOf(listOf(dir))
        every { musicScanner.scanDirectories(listOf("/storage/Music")) } returns listOf(
            songEntity(id = 0L, path = "/storage/Music/new.mp3", title = "New Song")
        )
        every { songDao.getAll() } returns flowOf(emptyList())
        coEvery { songDao.insertAll(any()) } returns emptyList()

        val result = repository.refreshSongs()

        assertTrue(result.isSuccess)
        coVerify { songDao.insertAll(match { it.size == 1 && it[0].path == "/storage/Music/new.mp3" }) }
    }

    @Test
    fun `refreshSongs deletes removed songs`() = runTest(testDispatcher) {
        val dir = scanDirectoryEntity(path = "/storage/Music")
        val existingSong = songEntity(id = 10L, path = "/storage/Music/old.mp3", title = "Old Song")

        every { scanDirectoryDao.getAll() } returns flowOf(listOf(dir))
        every { musicScanner.scanDirectories(listOf("/storage/Music")) } returns emptyList()
        every { songDao.getAll() } returns flowOf(listOf(existingSong))
        coEvery { songDao.delete(any()) } returns Unit

        val result = repository.refreshSongs()

        assertTrue(result.isSuccess)
        coVerify { songDao.delete(match { it.id == 10L && it.path == "/storage/Music/old.mp3" }) }
    }

    @Test
    fun `refreshSongs returns failure on exception`() = runTest(testDispatcher) {
        every { scanDirectoryDao.getAll() } throws RuntimeException("DB error")

        val result = repository.refreshSongs()

        assertTrue(result.isFailure)
    }

    @Test
    fun `searchSongs filters by title and artist`() = runTest(testDispatcher) {
        val songs = listOf(
            songEntity(id = 1L, title = "Hello World", artist = "Artist A"),
            songEntity(id = 2L, title = "Goodbye", artist = "Hello B"),
            songEntity(id = 3L, title = "Random", artist = "Random Artist")
        )
        every { songDao.getAll() } returns flowOf(songs)

        repository.searchSongs("hello").test {
            val results = awaitItem()
            assertEquals(2, results.size)
            assertEquals("Hello World", results[0].title)
            assertEquals("Goodbye", results[1].title) // artist "Hello B" matches
            awaitComplete()
        }
    }
}
