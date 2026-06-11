package com.rabbithole.musicbbit.data.local.sync

import com.rabbithole.musicbbit.data.local.model.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSyncEngineTest {

    @Test
    fun `sync returns inserts when songs are new`() {
        // Arrange
        val existing = emptyList<SongEntity>()
        val scanned = listOf(
            SongEntity(id = 0, path = "/music/a.mp3", title = "Song A", artist = null, album = null, durationMs = 180000, dateAdded = 0, coverUri = null),
            SongEntity(id = 0, path = "/music/b.mp3", title = "Song B", artist = null, album = null, durationMs = 200000, dateAdded = 0, coverUri = null)
        )
        val engine = SongSyncEngine()

        // Act
        val diff = engine.sync(existing, scanned)

        // Assert
        assertEquals(2, diff.toInsert.size)
        assertEquals("/music/a.mp3", diff.toInsert[0].path)
        assertEquals("/music/b.mp3", diff.toInsert[1].path)
        assertEquals(emptyList<SongEntity>(), diff.toDelete)
        assertEquals(emptyList<SongEntity>(), diff.toUpdate)
    }

    @Test
    fun `sync returns deletes when songs are removed`() {
        // Arrange
        val existing = listOf(
            SongEntity(id = 1, path = "/music/a.mp3", title = "Song A", artist = null, album = null, durationMs = 180000, dateAdded = 0, coverUri = null),
            SongEntity(id = 2, path = "/music/b.mp3", title = "Song B", artist = null, album = null, durationMs = 200000, dateAdded = 0, coverUri = null)
        )
        val scanned = emptyList<SongEntity>()
        val engine = SongSyncEngine()

        // Act
        val diff = engine.sync(existing, scanned)

        // Assert
        assertEquals(emptyList<SongEntity>(), diff.toInsert)
        assertEquals(2, diff.toDelete.size)
        assertEquals(1L, diff.toDelete[0].id)
        assertEquals(2L, diff.toDelete[1].id)
        assertEquals(emptyList<SongEntity>(), diff.toUpdate)
    }

    @Test
    fun `sync returns updates when metadata changes`() {
        // Arrange
        val existing = listOf(
            SongEntity(id = 1, path = "/music/a.mp3", title = "Song A", artist = "Artist A", album = null, durationMs = 180000, dateAdded = 1000, coverUri = null)
        )
        val scanned = listOf(
            SongEntity(id = 0, path = "/music/a.mp3", title = "Song A", artist = "Artist A Updated", album = "Album A", durationMs = 180000, dateAdded = 2000, coverUri = "/cover/a.jpg")
        )
        val engine = SongSyncEngine()

        // Act
        val diff = engine.sync(existing, scanned)

        // Assert
        assertEquals(emptyList<SongEntity>(), diff.toInsert)
        assertEquals(emptyList<SongEntity>(), diff.toDelete)
        assertEquals(1, diff.toUpdate.size)
        assertEquals(1L, diff.toUpdate[0].id)  // ID preserved
        assertEquals("Artist A Updated", diff.toUpdate[0].artist)
        assertEquals("Album A", diff.toUpdate[0].album)
        assertEquals(2000L, diff.toUpdate[0].dateAdded)
        assertEquals("/cover/a.jpg", diff.toUpdate[0].coverUri)
    }

    @Test
    fun `sync handles empty lists`() {
        // Arrange
        val existing = emptyList<SongEntity>()
        val scanned = emptyList<SongEntity>()
        val engine = SongSyncEngine()

        // Act
        val diff = engine.sync(existing, scanned)

        // Assert
        assertEquals(emptyList<SongEntity>(), diff.toInsert)
        assertEquals(emptyList<SongEntity>(), diff.toDelete)
        assertEquals(emptyList<SongEntity>(), diff.toUpdate)
    }

    @Test
    fun `sync handles identical lists`() {
        // Arrange
        val existing = listOf(
            SongEntity(id = 1, path = "/music/a.mp3", title = "Song A", artist = null, album = null, durationMs = 180000, dateAdded = 0, coverUri = null)
        )
        val scanned = listOf(
            SongEntity(id = 0, path = "/music/a.mp3", title = "Song A", artist = null, album = null, durationMs = 180000, dateAdded = 0, coverUri = null)
        )
        val engine = SongSyncEngine()

        // Act
        val diff = engine.sync(existing, scanned)

        // Assert
        assertEquals(emptyList<SongEntity>(), diff.toInsert)
        assertEquals(emptyList<SongEntity>(), diff.toDelete)
        assertEquals(emptyList<SongEntity>(), diff.toUpdate)
    }

    @Test
    fun `sync handles mixed operations`() {
        // Arrange
        val existing = listOf(
            SongEntity(id = 1, path = "/music/a.mp3", title = "Song A", artist = "Old", album = null, durationMs = 180000, dateAdded = 0, coverUri = null),
            SongEntity(id = 2, path = "/music/b.mp3", title = "Song B", artist = null, album = null, durationMs = 200000, dateAdded = 0, coverUri = null)
        )
        val scanned = listOf(
            SongEntity(id = 0, path = "/music/a.mp3", title = "Song A", artist = "New", album = null, durationMs = 180000, dateAdded = 0, coverUri = null),
            SongEntity(id = 0, path = "/music/c.mp3", title = "Song C", artist = null, album = null, durationMs = 220000, dateAdded = 0, coverUri = null)
        )
        val engine = SongSyncEngine()

        // Act
        val diff = engine.sync(existing, scanned)

        // Assert
        assertEquals(1, diff.toInsert.size)
        assertEquals("/music/c.mp3", diff.toInsert[0].path)
        assertEquals(1, diff.toDelete.size)
        assertEquals(2L, diff.toDelete[0].id)
        assertEquals(1, diff.toUpdate.size)
        assertEquals(1L, diff.toUpdate[0].id)
        assertEquals("New", diff.toUpdate[0].artist)
    }
}
