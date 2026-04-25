package com.rabbithole.musicbbit.data.local.dao

import com.rabbithole.musicbbit.data.model.PlaylistEntity
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistDaoTest : DatabaseTest() {

    private val dao by lazy { db.playlistDao() }

    @Test
    fun insert_returnsId() = dbTest {
        val playlist = PlaylistEntity(
            name = "My Playlist",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        val id = dao.insert(playlist)

        assertTrue(id > 0)
    }

    @Test
    fun getById_returnsEntity_whenExists() = dbTest {
        val playlist = PlaylistEntity(
            name = "My Playlist",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
        val id = dao.insert(playlist)

        val result = dao.getById(id)

        assertNotNull(result)
        assertEquals(id, result?.id)
        assertEquals("My Playlist", result?.name)
    }

    @Test
    fun getById_returnsNull_whenNotExists() = dbTest {
        val result = dao.getById(999L)

        assertNull(result)
    }

    @Test
    fun getAll_emitsPlaylists() = dbTest {
        val playlist1 = PlaylistEntity(
            name = "Playlist A",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
        val playlist2 = PlaylistEntity(
            name = "Playlist B",
            createdAt = 1_700_000_001_000L,
            updatedAt = 1_700_000_001_000L
        )
        dao.insert(playlist1)
        dao.insert(playlist2)

        val result = dao.getAll().first()

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Playlist A" })
        assertTrue(result.any { it.name == "Playlist B" })
    }

    @Test
    fun update_modifiesEntity() = dbTest {
        val playlist = PlaylistEntity(
            name = "Old Name",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
        val id = dao.insert(playlist)
        val inserted = dao.getById(id)!!
        val updated = inserted.copy(name = "New Name", updatedAt = 1_700_000_001_000L)

        dao.update(updated)
        val result = dao.getById(id)

        assertEquals("New Name", result?.name)
        assertEquals(1_700_000_001_000L, result?.updatedAt)
    }

    @Test
    fun delete_removesEntity() = dbTest {
        val playlist = PlaylistEntity(
            name = "To Delete",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
        val id = dao.insert(playlist)
        val inserted = dao.getById(id)!!

        dao.delete(inserted)
        val result = dao.getById(id)

        assertNull(result)
    }
}
