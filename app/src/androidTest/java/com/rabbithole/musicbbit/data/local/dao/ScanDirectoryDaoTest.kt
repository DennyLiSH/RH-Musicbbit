package com.rabbithole.musicbbit.data.local.dao

import com.rabbithole.musicbbit.data.model.ScanDirectoryEntity
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDirectoryDaoTest : DatabaseTest() {

    private val dao by lazy { db.scanDirectoryDao() }

    @Test
    fun insert_returnsId() = dbTest {
        val directory = ScanDirectoryEntity(
            path = "/storage/emulated/0/Music",
            name = "Music"
        )

        val id = dao.insert(directory)

        assertTrue(id > 0)
    }

    @Test
    fun getById_returnsEntity_whenExists() = dbTest {
        val directory = ScanDirectoryEntity(
            path = "/storage/emulated/0/Music",
            name = "Music"
        )
        val id = dao.insert(directory)

        val result = dao.getById(id)

        assertNotNull(result)
        assertEquals(id, result?.id)
        assertEquals("/storage/emulated/0/Music", result?.path)
        assertEquals("Music", result?.name)
        assertTrue(result?.addedAt != null && result.addedAt > 0)
    }

    @Test
    fun getById_returnsNull_whenNotExists() = dbTest {
        val result = dao.getById(999L)

        assertNull(result)
    }

    @Test
    fun getAll_emitsDirectories() = dbTest {
        val directory1 = ScanDirectoryEntity(
            path = "/storage/emulated/0/Music",
            name = "Music"
        )
        val directory2 = ScanDirectoryEntity(
            path = "/storage/emulated/0/Download",
            name = "Download"
        )
        dao.insert(directory1)
        dao.insert(directory2)

        val result = dao.getAll().first()

        assertEquals(2, result.size)
    }

    @Test
    fun delete_removesEntity() = dbTest {
        val directory = ScanDirectoryEntity(
            path = "/storage/emulated/0/Music",
            name = "Music"
        )
        val id = dao.insert(directory)
        val inserted = dao.getById(id)!!

        dao.delete(inserted)
        val result = dao.getById(id)

        assertNull(result)
    }
}
