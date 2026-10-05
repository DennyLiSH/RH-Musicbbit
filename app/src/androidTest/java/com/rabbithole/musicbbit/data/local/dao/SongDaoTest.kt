package com.rabbithole.musicbbit.data.local.dao

import com.rabbithole.musicbbit.data.local.model.SongEntity
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongDaoTest : DatabaseTest() {

    private val dao by lazy { db.songDao() }

    private fun song(
        path: String,
        title: String,
    ) = SongEntity(
        path = path,
        title = title,
        artist = "Artist A",
        album = "Album A",
        durationMs = 180_000L,
        dateAdded = 1_700_000_000_000L,
        coverUri = null
    )

    @Test
    fun insertAll_returnsIds() = dbTest {
        val songs = listOf(
            song("/music/song1.mp3", "Song One"),
            song("/music/song2.mp3", "Song Two"),
        )

        val ids = dao.insertAll(songs)

        assertEquals(2, ids.size)
        assertTrue(ids.all { it > 0 })
    }

    @Test
    fun getAll_emitsSongs() = dbTest {
        dao.insertAll(
            listOf(
                song("/music/song1.mp3", "Song One"),
                song("/music/song2.mp3", "Song Two"),
            )
        )

        val result = dao.getAll().first()

        assertEquals(2, result.size)
    }

    @Test
    fun delete_removesEntity() = dbTest {
        val id = dao.insertAll(listOf(song("/music/song1.mp3", "Song One"))).first()

        dao.delete(song("/music/song1.mp3", "Song One").copy(id = id))
        val result = dao.getAll().first()

        assertTrue(result.none { it.id == id })
    }

    @Test
    fun deleteAll_clearsAll() = dbTest {
        dao.insertAll(
            listOf(
                song("/music/song1.mp3", "Song One"),
                song("/music/song2.mp3", "Song Two"),
            )
        )

        dao.deleteAll()
        val result = dao.getAll().first()

        assertTrue(result.isEmpty())
    }
}
