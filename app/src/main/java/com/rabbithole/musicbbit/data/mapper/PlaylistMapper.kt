package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.local.model.PlaylistEntity
import com.rabbithole.musicbbit.domain.model.Playlist

object PlaylistMapper {
    fun PlaylistEntity.toDomain() = Playlist(id, name, createdAt, updatedAt)
    fun Playlist.toEntity() = PlaylistEntity(id, name, createdAt, updatedAt)
}
