package com.rabbithole.musicbbit.data.local.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity

data class PlaylistWithSongsEntity(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistSongEntity::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<SongEntity>
)
