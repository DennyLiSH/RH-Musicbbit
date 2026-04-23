package com.rabbithole.musicbbit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey
    val songId: Long,
    val positionMs: Long,
    val updatedAt: Long,
    val playlistId: Long?
)
