package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.local.model.PlaylistEntity
import com.rabbithole.musicbbit.data.mapper.PlaylistMapper.Companion.toDomain
import com.rabbithole.musicbbit.data.mapper.PlaylistMapper.Companion.toEntity
import com.rabbithole.musicbbit.domain.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistMapperTest {

    @Test
    fun `entity to domain preserves all fields`() {
        val entity = PlaylistEntity(id = 1, name = "Favorites", createdAt = 1000L, updatedAt = 2000L)
        val domain = entity.toDomain()
        assertEquals(Playlist(id = 1, name = "Favorites", createdAt = 1000L, updatedAt = 2000L), domain)
    }

    @Test
    fun `domain to entity preserves all fields`() {
        val domain = Playlist(id = 2, name = "Rock", createdAt = 3000L, updatedAt = 4000L)
        val entity = domain.toEntity()
        assertEquals(PlaylistEntity(id = 2, name = "Rock", createdAt = 3000L, updatedAt = 4000L), entity)
    }

    @Test
    fun `round trip entity-domain-entity is identity`() {
        val original = PlaylistEntity(id = 5, name = "Jazz", createdAt = 5000L, updatedAt = 6000L)
        val roundTripped = original.toDomain().toEntity()
        assertEquals(original, roundTripped)
    }
}
