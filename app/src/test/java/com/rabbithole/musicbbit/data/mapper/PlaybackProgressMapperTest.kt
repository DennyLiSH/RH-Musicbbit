package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.local.model.PlaybackProgressEntity
import com.rabbithole.musicbbit.data.mapper.toDomain
import com.rabbithole.musicbbit.data.mapper.toEntity
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressMapperTest {

    @Test
    fun `entity to domain preserves all fields`() {
        val entity = PlaybackProgressEntity(
            songId = 1L, positionMs = 30000L, updatedAt = 1000L, playlistId = 5L
        )
        val domain = entity.toDomain()
        assertEquals(
            PlaybackProgress(songId = 1L, positionMs = 30000L, updatedAt = 1000L, playlistId = 5L),
            domain
        )
    }

    @Test
    fun `domain to entity preserves all fields`() {
        val domain = PlaybackProgress(
            songId = 2L, positionMs = 60000L, updatedAt = 2000L, playlistId = 10L
        )
        val entity = domain.toEntity()
        assertEquals(
            PlaybackProgressEntity(songId = 2L, positionMs = 60000L, updatedAt = 2000L, playlistId = 10L),
            entity
        )
    }

    @Test
    fun `round trip entity-domain-entity is identity`() {
        val original = PlaybackProgressEntity(
            songId = 99L, positionMs = 120000L, updatedAt = 9999L, playlistId = 42L
        )
        val roundTripped = original.toDomain().toEntity()
        assertEquals(original, roundTripped)
    }
}
