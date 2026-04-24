package com.rabbithole.musicbbit.domain.model

data class PlaybackProgress(
    val songId: Long,
    val positionMs: Long,
    val updatedAt: Long,
    val playlistId: Long
)
