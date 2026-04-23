package com.rabbithole.musicbbit.domain.model

data class PlaylistWithSongs(
    val playlist: Playlist,
    val songs: List<Song>
)
