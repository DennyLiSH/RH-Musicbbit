package com.rabbithole.musicbbit.domain.model

/** Alarm joined with its playlist name for list display. Null name = playlist deleted. */
data class AlarmWithPlaylistName(
    val alarm: Alarm,
    val playlistName: String?,
)