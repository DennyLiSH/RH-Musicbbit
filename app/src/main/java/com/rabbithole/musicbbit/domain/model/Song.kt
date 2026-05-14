package com.rabbithole.musicbbit.domain.model

data class Song(
    val id: Long = 0,
    val path: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val dateAdded: Long,
    val coverUri: String?
)
