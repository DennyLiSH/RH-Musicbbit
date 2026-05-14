package com.rabbithole.musicbbit.domain.model

data class Playlist(
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)
