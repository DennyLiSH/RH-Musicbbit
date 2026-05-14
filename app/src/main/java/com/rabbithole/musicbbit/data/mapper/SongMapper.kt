package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.domain.model.Song

object SongMapper {
    fun SongEntity.toDomain() = Song(id, path, title, artist, album, durationMs, dateAdded, coverUri)
    fun Song.toEntity() = SongEntity(id, path, title, artist, album, durationMs, dateAdded, coverUri)
}
