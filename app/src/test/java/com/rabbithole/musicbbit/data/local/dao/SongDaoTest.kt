package com.rabbithole.musicbbit.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rabbithole.musicbbit.data.local.AppDatabase
import com.rabbithole.musicbbit.data.local.model.SongEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SongDaoTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var database: AppDatabase
    private lateinit var songDao: SongDao

    @Before
    fun setup() {
        hiltRule.inject()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        songDao = database.songDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `getByPathPrefix returns songs with matching prefix`() = runTest {
        // Arrange
        val songs = listOf(
            SongEntity(0, "/storage/Music/song1.mp3", "Song 1", "Artist", "Album", 180000, System.currentTimeMillis(), null),
            SongEntity(0, "/storage/Music/song2.mp3", "Song 2", "Artist", "Album", 180000, System.currentTimeMillis(), null),
            SongEntity(0, "/storage/Downloads/song3.mp3", "Song 3", "Artist", "Album", 180000, System.currentTimeMillis(), null)
        )
        songDao.insertAll(songs)

        // Act
        val result = songDao.getByPathPrefix("/storage/Music").first()

        // Assert
        assertEquals(2, result.size)
        assertEquals("/storage/Music/song1.mp3", result[0].path)
        assertEquals("/storage/Music/song2.mp3", result[1].path)
    }

    @Test
    fun `getByPathPrefix does not match similar prefix`() = runTest {
        // Arrange
        val songs = listOf(
            SongEntity(0, "/storage/Music/song1.mp3", "Song 1", "Artist", "Album", 180000, System.currentTimeMillis(), null),
            SongEntity(0, "/storage/Music2/song2.mp3", "Song 2", "Artist", "Album", 180000, System.currentTimeMillis(), null)
        )
        songDao.insertAll(songs)

        // Act
        val result = songDao.getByPathPrefix("/storage/Music").first()

        // Assert
        assertEquals(1, result.size)
        assertEquals("/storage/Music/song1.mp3", result[0].path)
    }

    @Test
    fun `getByPathPrefix matches exact path`() = runTest {
        // Arrange
        val song = SongEntity(0, "/storage/Music", "Song", "Artist", "Album", 180000, System.currentTimeMillis(), null)
        songDao.insertAll(listOf(song))

        // Act
        val result = songDao.getByPathPrefix("/storage/Music").first()

        // Assert
        assertEquals(1, result.size)
        assertEquals("/storage/Music", result[0].path)
    }

    @Test
    fun `getByPathPrefix returns empty list when no matches`() = runTest {
        // Arrange
        val song = SongEntity(0, "/storage/Downloads/song.mp3", "Song", "Artist", "Album", 180000, System.currentTimeMillis(), null)
        songDao.insertAll(listOf(song))

        // Act
        val result = songDao.getByPathPrefix("/storage/Music").first()

        // Assert
        assertEquals(0, result.size)
    }
}
