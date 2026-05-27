package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song

fun toPlaylistWithSongs(
    playlist: Playlist,
    songs: List<Song>,
    sortOrders: List<PlaylistSongEntity>
): PlaylistWithSongs {
    val sortOrderMap = sortOrders.associate { it.songId to it.sortOrder }
    val sortedSongs = songs.sortedBy { sortOrderMap[it.id] ?: Int.MAX_VALUE }
    return PlaylistWithSongs(playlist = playlist, songs = sortedSongs)
}
