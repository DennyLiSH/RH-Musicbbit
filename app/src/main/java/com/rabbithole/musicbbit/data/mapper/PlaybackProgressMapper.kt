package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.local.model.PlaybackProgressEntity
import com.rabbithole.musicbbit.domain.model.PlaybackProgress

object PlaybackProgressMapper {
    fun PlaybackProgressEntity.toDomain() = PlaybackProgress(songId, positionMs, updatedAt, playlistId)
    fun PlaybackProgress.toEntity() = PlaybackProgressEntity(songId, positionMs, updatedAt, playlistId)
}
