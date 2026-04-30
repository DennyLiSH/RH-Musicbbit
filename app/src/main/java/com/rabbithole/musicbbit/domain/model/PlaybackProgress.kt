package com.rabbithole.musicbbit.domain.model

import androidx.room.Entity

@Entity(tableName = "playback_progress", primaryKeys = ["songId", "playlistId"])
data class PlaybackProgress(
    val songId: Long,
    val positionMs: Long,
    val updatedAt: Long,
    val playlistId: Long
)
