package com.rabbithole.musicbbit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val dateAdded: Long,
    val coverUri: String?
)
