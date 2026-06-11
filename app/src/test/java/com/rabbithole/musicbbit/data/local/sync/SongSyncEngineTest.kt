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
}
