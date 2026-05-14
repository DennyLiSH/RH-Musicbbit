package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.data.mapper.SongMapper.Companion.toDomain
import com.rabbithole.musicbbit.data.mapper.SongMapper.Companion.toEntity
import com.rabbithole.musicbbit.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class SongMapperTest {

    @Test
    fun `entity to domain preserves all fields`() {
        val entity = SongEntity(
            id = 1, path = "/music/song.mp3", title = "Test Song",
            artist = "Artist", album = "Album", durationMs = 300000L,
            dateAdded = 1000L, coverUri = "content://cover/1"
        )
        val domain = entity.toDomain()
        assertEquals(
            Song(
                id = 1, path = "/music/song.mp3", title = "Test Song",
                artist = "Artist", album = "Album", durationMs = 300000L,
                dateAdded = 1000L, coverUri = "content://cover/1"
            ),
            domain
        )
    }

    @Test
    fun `domain to entity preserves all fields`() {
        val domain = Song(
            id = 2, path = "/music/other.flac", title = "Other Song",
            artist = null, album = null, durationMs = 180000L,
            dateAdded = 2000L, coverUri = null
        )
        val entity = domain.toEntity()
        assertEquals(
            SongEntity(
                id = 2, path = "/music/other.flac", title = "Other Song",
                artist = null, album = null, durationMs = 180000L,
                dateAdded = 2000L, coverUri = null
            ),
            entity
        )
    }

    @Test
    fun `round trip entity-domain-entity is identity`() {
        val original = SongEntity(
            id = 5, path = "/test/audio.wav", title = "Round Trip",
            artist = "Band", album = "EP", durationMs = 60000L,
            dateAdded = 5000L, coverUri = "content://cover/5"
        )
        val roundTripped = original.toDomain().toEntity()
        assertEquals(original, roundTripped)
    }
}
