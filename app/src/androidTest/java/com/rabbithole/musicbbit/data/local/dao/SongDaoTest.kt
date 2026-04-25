package com.rabbithole.musicbbit.data.local.dao

import com.rabbithole.musicbbit.data.model.SongEntity
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongDaoTest : DatabaseTest() {

    private val dao by lazy { db.songDao() }

    @Test
    fun insert_returnsId() = dbTest {
        val song = SongEntity(
            path = "/music/song1.mp3",
            title = "Song One",
            artist = "Artist A",
            album = "Album A",
            durationMs = 180_000L,
            dateAdded = 1_700_000_000_000L,
            coverUri = null
        )

        val id = dao.insert(song)

        assertTrue(id > 0)
    }

    @Test
    fun insertAll_returnsIds() = dbTest {
        val songs = listOf(
            SongEntity(
                path = "/music/song1.mp3",
                title = "Song One",
                artist = "Artist A",
                album = "Album A",
                durationMs = 180_000L,
                dateAdded = 1_700_000_000_000L,
                coverUri = null
            ),
            SongEntity(
                path = "/music/song2.mp3",
                title = "Song Two",
                artist = "Artist B",
                album = "Album B",
                durationMs = 240_000L,
                dateAdded = 1_700_000_001_000L,
                coverUri = null
            )
        )

        val ids = dao.insertAll(songs)

        assertEquals(2, ids.size)
        assertTrue(ids.all { it > 0 })
    }

    @Test
    fun getById_returnsEntity_whenExists() = dbTest {
        val song = SongEntity(
            path = "/music/song1.mp3",
            title = "Song One",
            artist = "Artist A",
            album = "Album A",
            durationMs = 180_000L,
            dateAdded = 1_700_000_000_000L,
            coverUri = null
        )
        val id = dao.insert(song)

        val result = dao.getById(id)

        assertNotNull(result)
        assertEquals(id, result?.id)
        assertEquals("Song One", result?.title)
        assertEquals("Artist A", result?.artist)
    }

    @Test
    fun getById_returnsNull_whenNotExists() = dbTest {
        val result = dao.getById(999L)

        assertNull(result)
    }

    @Test
    fun getAll_emitsSongs() = dbTest {
        val song1 = SongEntity(
            path = "/music/song1.mp3",
            title = "Song One",
            artist = "Artist A",
            album = "Album A",
            durationMs = 180_000L,
            dateAdded = 1_700_000_000_000L,
            coverUri = null
        )
        val song2 = SongEntity(
            path = "/music/song2.mp3",
            title = "Song Two",
            artist = "Artist B",
            album = "Album B",
            durationMs = 240_000L,
            dateAdded = 1_700_000_001_000L,
            coverUri = null
        )
        dao.insert(song1)
        dao.insert(song2)

        val result = dao.getAll().first()

        assertEquals(2, result.size)
    }

    @Test
    fun update_modifiesEntity() = dbTest {
        val song = SongEntity(
            path = "/music/song1.mp3",
            title = "Old Title",
            artist = "Artist A",
            album = "Album A",
            durationMs = 180_000L,
            dateAdded = 1_700_000_000_000L,
            coverUri = null
        )
        val id = dao.insert(song)
        val inserted = dao.getById(id)!!
        val updated = inserted.copy(title = "New Title", artist = "New Artist")

        dao.update(updated)
        val result = dao.getById(id)

        assertEquals("New Title", result?.title)
        assertEquals("New Artist", result?.artist)
    }

    @Test
    fun delete_removesEntity() = dbTest {
        val song = SongEntity(
            path = "/music/song1.mp3",
            title = "Song One",
            artist = "Artist A",
            album = "Album A",
            durationMs = 180_000L,
            dateAdded = 1_700_000_000_000L,
            coverUri = null
        )
        val id = dao.insert(song)
        val inserted = dao.getById(id)!!

        dao.delete(inserted)
        val result = dao.getById(id)

        assertNull(result)
    }

    @Test
    fun deleteAll_clearsAll() = dbTest {
        val song1 = SongEntity(
            path = "/music/song1.mp3",
            title = "Song One",
            artist = "Artist A",
            album = "Album A",
            durationMs = 180_000L,
            dateAdded = 1_700_000_000_000L,
            coverUri = null
        )
        val song2 = SongEntity(
            path = "/music/song2.mp3",
            title = "Song Two",
            artist = "Artist B",
            album = "Album B",
            durationMs = 240_000L,
            dateAdded = 1_700_000_001_000L,
            coverUri = null
        )
        dao.insert(song1)
        dao.insert(song2)

        dao.deleteAll()
        val result = dao.getAll().first()

        assertTrue(result.isEmpty())
    }
}
