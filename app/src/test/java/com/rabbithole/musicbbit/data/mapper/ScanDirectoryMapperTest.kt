package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.local.model.ScanDirectoryEntity
import com.rabbithole.musicbbit.data.mapper.ScanDirectoryMapper.Companion.toDomain
import com.rabbithole.musicbbit.data.mapper.ScanDirectoryMapper.Companion.toEntity
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanDirectoryMapperTest {

    @Test
    fun `entity to domain preserves all fields`() {
        val entity = ScanDirectoryEntity(id = 1, path = "/storage/Music", name = "Music", addedAt = 1000L)
        val domain = entity.toDomain()
        assertEquals(ScanDirectory(id = 1, path = "/storage/Music", name = "Music", addedAt = 1000L), domain)
    }

    @Test
    fun `domain to entity preserves all fields`() {
        val domain = ScanDirectory(id = 2, path = "/storage/Downloads", name = "Downloads", addedAt = 2000L)
        val entity = domain.toEntity()
        assertEquals(ScanDirectoryEntity(id = 2, path = "/storage/Downloads", name = "Downloads", addedAt = 2000L), entity)
    }

    @Test
    fun `round trip entity-domain-entity is identity`() {
        val original = ScanDirectoryEntity(id = 5, path = "/test", name = "Test", addedAt = 5000L)
        val roundTripped = original.toDomain().toEntity()
        assertEquals(original, roundTripped)
    }
}
