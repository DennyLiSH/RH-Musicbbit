package com.rabbithole.musicbbit.data.model

import androidx.room.Entity

@Entity(tableName = "playback_progress", primaryKeys = ["songId", "playlistId"])
data class PlaybackProgressEntity(
    val songId: Long,
    val positionMs: Long,
    val updatedAt: Long,
    val playlistId: Long
)
